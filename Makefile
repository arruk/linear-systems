comb:
	sbt "testOnly comb.CombSpec"

md:
	pandoc tutorial.md | lynx -stdin

