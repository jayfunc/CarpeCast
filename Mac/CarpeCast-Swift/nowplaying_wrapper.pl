#!/usr/bin/perl
use strict;
use warnings;
require DynaLoader;

my $dylib = shift or die "Missing dylib path";
my $handle = DynaLoader::dl_load_file($dylib, 0) or die "Failed to load $dylib";
my $symbol = DynaLoader::dl_find_symbol($handle, "run_helper") or die "Symbol not found";
DynaLoader::dl_install_xsub("main::run_helper", $symbol);

main::run_helper();
