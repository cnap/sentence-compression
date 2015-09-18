#!/usr/bin/perl

# This script changes the spacing of fields in an ARPA LM to be tab separated
# (instead of space separated) and removes trailing white space. See README
# for more information.
#
# Sample usage:
# gzcat cmusphinx-5.0-en-us.lm.gz | perl fix_spacing.pl | gzip > cmusphinx-5.0-en-us.lm.fixed.gz
#
# Courtney Napoles, cdnapoles@gmail.com

use strict;

my $n = 1;
my $in_ngrams = 0;

while (1) {
  my $l = <>;
  if (! $l) {
    last;
  }
  
  $l =~ s/\s+$//;
  if ($l =~ /^$/) {
    print "$l\n";
    next;
  }

  if ($l =~ /^\\(\d)/) {
    $n = $1;
    $in_ngrams = 1;
    print "$l\n";
    next;
  }
  
  if ($in_ngrams) {
    $l =~ s/\s/\t/;
    if ((split(/\s+/,$l)) > $n + 1) {
      $l =~ s/\s(\S+)$/\t$1/;
    } 
  }
  print "$l\n";
}
