# Copyright (C) 2007, 2008, 2009, 2010, 2011, 2012, 2013 Apple Inc.  All rights reserved.
# 3.  Neither the name of Apple Computer, Inc. ("Apple") nor the names of
        &changeLogFileName
    if ($^O eq "MSWin32") {
sub isGitSVN()
    return $isGitSVN if defined $isGitSVN;
    $isGitSVN = $output ne '';
    return eval "v$version" ge v1.6;
        return dirname(gitDirectory());
sub changeLogSuffix()
{
    my $rootPath = determineVCSRoot();
    my $changeLogSuffixFile = File::Spec->catfile($rootPath, ".changeLogSuffix");
    return "" if ! -e $changeLogSuffixFile;
    open FILE, $changeLogSuffixFile or die "Could not open $changeLogSuffixFile: $!";
    my $changeLogSuffix = <FILE>;
    chomp $changeLogSuffix;
    close FILE;
    return $changeLogSuffix;
}

sub changeLogFileName()
{
    return "ChangeLog" . changeLogSuffix()
}

    my $name = $ENV{CHANGE_LOG_NAME} || $ENV{REAL_NAME} || gitConfig("user.name") || (split /\s*,\s*/, (getpwuid $<)[6])[0];
    if ($contents !~ m"\nGIT binary patch\n$gitPatchRegExp$gitPatchRegExp\Z") {
        die "$fullPath: unknown git binary patch format"