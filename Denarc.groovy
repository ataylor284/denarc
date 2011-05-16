#!/usr/bin/env groovy

import groovy.transform.Immutable

@Grab(group='org.ccil.cowan.tagsoup', module='tagsoup', version='1.2')

@Immutable class Violation {
	String ruleName
	String sourceCode
}

@Immutable class FixRecipe {
	List rules   // list of CodeNarc violations this recipe can fix
	Closure action  // closure to fix a line with a violation
}

def recipes = [
	[rules: ['#UnusedImport', '#UnnecessaryGroovyImport', '#ImportFromSamePackage'],
	 action: { line -> null } ] as FixRecipe,
	[rules: ['#UnnecessaryPublicModifier'],
	 action: { line -> line?.replaceAll("public ", "") } ] as FixRecipe,
	[rules: ['#UnnecessaryDefInMethodDeclaration'],
	 action: { line -> line?.replaceAll("def ", "") } ] as FixRecipe
]

def doc = new XmlSlurper(new org.ccil.cowan.tagsoup.Parser()).parse(System.in)
def currentPackage

for (summaryDiv in doc.body.div.findAll { it.@class == 'summary' }) {

	// somewhat hacky way of recreating the groovy filename from packageHeader and fileHeader tag contents
	def packageHeader = summaryDiv.h2.find { it.@class == 'packageHeader' }
	if (packageHeader) { 
		def m = packageHeader.text() =~ /Package: (.*)/
		currentPackage = m[0][1]
	}
	def filename
	def fileHeader = summaryDiv.h3.find { it.@class == 'fileHeader' }
	if (fileHeader) {
		def m = fileHeader.text() =~ /..(.*)/
		filename = "$currentPackage/${m[0][1]}".replaceAll('\\.', "/").replaceAll('/groovy$', ".groovy")
	}

	if (!summaryDiv.h3.find { it.@class == 'fileHeader' } )
		continue;

	// collect all the CodeNarc violations for this file into a list of [recipe, violation] pairs
	def fixes = summaryDiv.table.tr.collect { row ->
		def violation = new Violation(row.td[0].a.@href.text(), String.format("%.56s", row.td[3].p.span[1].text()))
		def recipe = recipes.find { rule -> rule.rules.contains(violation.ruleName) }
		[recipe, violation]
	}.findAll { recipe, violation -> 
		// filter out violations without any matching recipes
		recipe && violation
	}

	// if some fixable violations were found, rewrite the file with recipe actions applied
	// FIXME: use line number in CodeNarc report instead regexp matching?
	if (fixes) {
		def orig = new File(filename)
		new File(filename + '.new').withPrintWriter { replacement ->
			orig.eachLine { line ->
				fixes.each { recipe, violation ->
					if (line && line.trim().contains(String.format("%.56s", violation.sourceCode.trim()))) {
						println "replacing $line with"
						line = recipe.action(line)
						println " $line ($recipe, $violation)"
					}
				}
				if (line != null) {
					replacement.println(line)
				}
			}
		}
		orig.renameTo(filename + '.old')
		new File(filename + '.new').renameTo(filename)
	}
}
