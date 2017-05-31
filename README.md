# Sentence compression

## Note about ILOG CPLEX

This program depends on the professional version of CPLEX, since the trial version limits the problem size. CPLEX version 12.6 is required (any other versions are not currently supported and may not function).

The full version of ILOG CPLEX is available to academics through the [IBM Academic Initiative](https://developer.ibm.com/academic/).

## ABOUT

This program generates sentence-level compressions via deletion. It is
a modified implementation of the ILP model described in Clarke and 
Lapata, 2008, "Global Inference for Sentence Compression: An Integer 
Linear Programming Approach".

## SETUP

```ant compile```

ILOG CPLEX needs to be installed to run, and the paths in `build.xml` and
`compress` should be updated accordingly.

## RUN

```./compress	
	Usage: ./compress -i path/to/input -l path/to/lm [-x]
	  -i val  input file or directory
	  -d      debug
	  -l val  path to language model (binary or arpa)
	  -t      output should be <= 120 characters
	  -q      suppress cplex output (normally goes to stderr)
	  -x      input file(s) in xml format
```

## INPUT

The program expects tokenized text with one sentence per line.

## OUTPUT

`<orig_len> <short_len>	<compression> <orig_indices> <compression_rate>`

For example, for the input sentence "At the camp , the rebel troops were welcomed 
                                     with a banner that read : `` Welcome home . ''",
the output is as follows:

`20  8	   At camp , the troops were welcomed .	1 3 4 5 7 8 9 19	0.4`

## JAVA CLASS

To generate extractive compressions (by deletion only) using an extended 
version of Clarke & Lapata (2008)'s ILP model:

```
java research.compression.SentenceCompressor
   Required arguments:
     -in=val		path to the input file or directory
     -lm=val		path to the language model (trigram)
   Optional arguments:
     -char		use character-based constraints
     -cr=val		minimum compression rate (default is 0.4)
     -debug             debug
     -l=val		specify lambda value (tradeoff between n-gram probability and
     			"significance" score in objective function
     -ngram		use the n-gram constraint (each n-gram in compression present in
     			Google n-grams; n-gram server must be running.
     -quiet             supress cplex output
     -target=val	specify the target compression length for each sentence
     -test_lambda	test varying values of lambda (for dev)
     -tweet		use a Twitter length constraint (120 characters)
     -xml		input is in xml format	 
```

Example call:
```
java -Xms2g -Xmx10g -Djava.library.path=$ILOG/bin/x86-64_osx \
   -cp bin:lib/berkeleylm.jar:$ILOG/lib/cplex.jar:lib/stanford-parser.jar \
   research.compression.SentenceCompressor -in=data/sample_text -lm=your_lm.gz
```

## LANGUAGE MODEL

The language model used is not provided for licensing issues. This software
requires a trigram language model in ARPA format. In our research, we used a
trigram language model trained on English Gigaword 5 using SRILM. There are some
language models available for download from http://www.keithv.com/software/giga/. Note that I
have not tested or used these models myself.

The LM reader used by this program expects each n-gram line to be in the format
    `log_prob<TAB>ngram<TAB>backoff`

If there is no backoff weight, then the format should be
   `log_prob<TAB>ngram`

If you get a `String index out of range` error, and your LM is in ARPA, the
fields may be space separated (instead of tab separated), or have trailing
spaces. I have added a script, `fix_spacing.pl` to fix this issue. To run this
script, call

```
zcat your_lm.gz | perl fix_spacing.pl | gzip > your_fixed_lm.gz
```

-----
last updated 31 May 2017
Courtney Napoles, napoles@cs.jhu.edu
