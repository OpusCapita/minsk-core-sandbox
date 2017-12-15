## Migrate grails plugins from mercurial to git repository

### Steps

1. Remove mercurial files **.hgtags** **.hgignore**
1. Rename file **CHANGES.txt** -> **CHANGELOG.md**
1. Create new files **.gitignore**, **.gitattributes**, **.editorconfig**, **Jenkinsfile**
1. Change vcs reference in plugin descriptor
1. Repeating steps 1-4 for branch 8.0.GA.DEV if this branch exist
1. Repeating steps 1-4 for branches bugfix-* if was changes in this branch for the last year
1. Remove all sources in **default** branch in mercurial repository, add **WHERE-IS.md** and close this branch
1. Create repository on github for organization **OpusCapita** and push all changes with all tags and dev branches
1. Add some admin collaborations for this repository


### Usage

> groovy **grails-plugins-hg2git.groovy**
  *[--help]*
  *[--github-user=<user>]*
  *[--github-password=<password>]*
  *[--base-dir=<path>]*
  *[--force-push-changes]*
  *[--add-collaborators=<user>:<role>,...]*
  
where:

  --help                                get help
  
  --github-user=<user>                  (REQUIRED) user name of Github social service, included to OpusCapita organization

  --github-password=<password>          (OPTIONAL) password, by default should be request from console input

  --base-dir=<path>                     (OPTIONAL) base dir, by default current folder

  --force-push-changes                  (OPTIONAL) push local changes to mercurial and git, by default is false

  --add-collaborators=<user>:<role>,... (OPTIONAL) push local changes to mercurial and git, by default is false


for example: 
```sh
groovy grails-plugins-hg2git.groovy --github-user=divin@scand.com --add-collaborators=asergeev-sc:admin --force-push-changes
```

List of grails plugins *grails-plugins-list.txt*
List of ignore grails plugins *grails-plugins-blacklist.txt*


#### Requirements

* Operation system - *linux*, *MacOS*
* Presence of installed commands *git*, *hg*, *curl*, */bin/sh*, *groovy*, *echo*