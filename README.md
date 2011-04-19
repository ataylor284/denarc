Denarc: an Automated CodeNarc Violation Fixer
=============================================

Denarc is a quick script to automatically fix a few of the simpler
codenarc rule violations.

It works by parsing the HTML report codenarc generates and rewriting
the groovy files with some regexp hackery.  The original files are
saved with the extension ".old" just in case.

Denarcable Rules
----------------

* UnusedImport
* UnnecessaryGroovyImport
* ImportFromSamePackage
* UnnecessaryPublicModifier
* UnnecessaryDefInMethodDeclaration

Andrew Taylor <ataylor@redtoad.ca>
