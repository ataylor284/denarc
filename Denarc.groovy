#!/usr/bin/env groovy

@Grab(group='org.ccil.cowan.tagsoup', module='tagsoup', version='1.2')

class FixRecipe {
	def rules   // list of CodeNarc violations this recipe can fix
	def action  // closure to fix a line with a violation
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

doc.body.div.findAll { it.@class == 'summary' }.each { summaryDiv ->

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

	// collect all the CodeNarc violations for this file into a list of [recipe, violations] pairs
	def fixes = recipes.collect { recipe ->
		[recipe, summaryDiv.table.tr.collect { row ->
			if (row.td.a.find { recipe.rules.contains(it.@href) }) {
				row.td.p.span.findAll { it.@class == 'sourceCode' }.collect { String.format("%.56s", it.text()) }
			}
		}.inject([], { list, value -> if (value) { list + value } else { list } })]
	}.findAll { recipe, violations -> 
		// filter out recipes without any violations
		violations 
	}

	// if some fixable violations were found, rewrite the file with recipe actions applied
	// FIXME: use line number in CodeNarc report instead regexp matching?
	if (fixes) {
		def orig = new File(filename)
		new File(filename + '.new').withPrintWriter { replacement ->
			orig.eachLine { line ->
				for (fix in fixes) {
					if (line && fix[1].contains(String.format("%.56s", line.trim()))) {
						line = fix[0].action(line)
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
