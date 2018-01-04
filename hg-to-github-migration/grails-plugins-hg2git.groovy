@Grab('log4j:log4j:1.2.17')
@Grab(group = 'commons-io', module = 'commons-io', version = '2.4')
import org.apache.log4j.*
import org.apache.commons.io.FileUtils

import java.text.SimpleDateFormat

def printHelp = {
    println "usage: groovy grails-plugins-hg2git.groovy [--help] [--github-user=<user>] [--github-password=<password>]"
    println "                             [--base-dir=<path>] [--force-push-changes] [--add-collaborators=<user>:<role>,...]"
    println "\nwhere:"
    println "  --help                                get help"
    println "  --github-user=<user>                  (REQUIRED) user name of Github social service, included to OpusCapita organization"
    println "  --github-password=<password>          (OPTIONAL) password, by default should be request from console input"
    println "  --base-dir=<path>                     (OPTIONAL) base dir, by default current folder"
    println "  --force-push-changes                  (OPTIONAL) push local changes to mercurial and git, by default is false"
    println "  --add-collaborators=<user>:<role>,... (OPTIONAL) push local changes to mercurial and git, by default is false\n"
    println "for example: groovy grails-plugins-hg2git.groovy --github-user=divin@scand.com --add-collaborators=asergeev-sc:admin --force-push-changes\n\n"
}

def parseArguments = { String[] arguments, Map defaultArgs = [:] ->
    Map parsedMap = arguments.findAll { it.startsWith('--') }.collectEntries {
        String arg0 = it.substring(2, it.length())
        def res = arg0.split('=')

        if (res.size() == 1) {
            return [res[0].trim(), true]
        } else {
            return [res[0].trim(), res[1].trim()]
        }
    }

    (parsedMap + [isDefault: {
        !parsedMap[it] && defaultArgs[it]
    }]).withDefault { key ->
        if (parsedMap.containsKey(key)) {
            return parsedMap[key]
        } else if (defaultArgs.containsKey(key)) {
            def value = defaultArgs[key]
            if (value instanceof Closure) {
                return value()
            } else {
                return value
            }
        } else {
            return null
        }
    }
}

def config = new ConfigSlurper().parse(new File('./log4j.groovy').text)
PropertyConfigurator.configure(config.toProperties())

def log = Logger.getLogger('grails-plugins-hg2git')

Map arguments = parseArguments(args, [
        'github-password'   : {
            print "github-password:"
            System.console().readPassword()?.toString()
        },
        'base-dir'          : './',
        'force-push-changes': false
])

if (arguments['help']) {
    printHelp()
    return
}

Map collaborators = [:]
if (arguments['add-collaborators']) {
    for (e in arguments['add-collaborators'].split(',')) {
        List collaborator = e.trim().split(':')

        if (collaborator.size() == 2) {
            collaborators[collaborator[0].trim()] = collaborator[1].trim()
        }
    }
}

Boolean forcePushChanges = arguments['force-push-changes']

String gitHubUser = arguments['github-user']

if (!gitHubUser) {
    printHelp()
    log.error "--github-user=<user> - argument should be required"
    return
}
String gitHubPassword = forcePushChanges ? arguments['github-password'] : null

if (!arguments.isDefault('github-password')) {
    println "Get password from argument not secured, don't remember cleaning your .bash_history"
    println "Got it, press any key continue"
    System.in.read()
}

println arguments['base-dir']
File basedir = new File(arguments['base-dir'])

if (!basedir.exists()) {
    log.error "base dir ${basedir.absolutePath} doesn't exists"
    return
}

File hgRootDir = new File(basedir, "hg")
File gitRootDir = new File(basedir, "git")

!hgRootDir.exists() && hgRootDir.mkdir()
!gitRootDir.exists() && gitRootDir.mkdir()

def getMercurialBranches = { File hgDir ->
    [

            '/bin/sh',
            '-c',
            'hg branche|awk \'{ print $1 }\''
    ].execute([], hgDir).text.readLines()
}

def isActiveMercurialBranchByLastYear = { branch, File hgDir ->
    def dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    use(groovy.time.TimeCategory) {
        ([
                '/bin/sh',
                '-c',
                "hg log -b ${branch} -d \"${dateFormat.format(new Date() - 1.year)} to ${dateFormat.format(new Date() + 1.day)}\"|wc -l"
        ].execute([], hgDir).text as Integer) > 0
    }
}

def executeCommand = { cmd, File dir ->
    def createPrintStreamLoggingProxy = { Closure loggerMethod, PrintStream printStream ->
        new PrintStream(printStream) {
            public void print(final String string) {
                printStream.print(string)
                loggerMethod(string)
            }
        }
    }
    log.info "execute command ${cmd in List ? cmd.join(' ') : cmd} in ${dir}"
    cmd.execute(System.getenv().collect { k, v -> "$k=$v" }, dir).waitForProcessOutput(
//            createPrintStreamLoggingProxy(log.&info, System.out),
//            createPrintStreamLoggingProxy(log.&error, System.err)
            System.out,
            System.err,
    )
}

def changeLocalGitFiles = { repoName, url, File gitRepoDir ->
    for (file in new File('./git-files').listFiles()) {
        log.info "copy file ${file.absolutePath} to ${gitRepoDir.absolutePath}"
        FileUtils.copyFileToDirectory(file, gitRepoDir)
    }
    executeCommand("git add .editorconfig", gitRepoDir)
    executeCommand("git add .gitattributes", gitRepoDir)
    executeCommand("git add .gitignore", gitRepoDir)
    executeCommand("git add Jenkinsfile", gitRepoDir)
    executeCommand("git rm .hgignore", gitRepoDir)

    File pluginDescriptorFilePath = gitRepoDir.listFiles().find { it.name.endsWith("GrailsPlugin.groovy") }
    if (!pluginDescriptorFilePath) {
        log.error "plugin descriptor not found in dir ${gitRepoDir.absolutePath}/GrailsPlugin.groovy"
        return
    } else {
        def length = url.length() + 1
        def index = pluginDescriptorFilePath.text.indexOf(url + "/")
        if (index == -1) {
            length = url.length()
            index = pluginDescriptorFilePath.text.indexOf(url)
        }
        if (index == -1) {
            length = "http://buildsever.jcatalog.com/hg/${repoName}/".length()
            index = pluginDescriptorFilePath.text.indexOf("http://buildsever.jcatalog.com/hg/${repoName}/")
        }
        if (index == -1) {
            length = "http://buildsever.jcatalog.com/hg/${repoName}".length()
            index = pluginDescriptorFilePath.text.indexOf("http://buildsever.jcatalog.com/hg/${repoName}")
        }
        if (index == -1) {
            length = "http://buildsever.jcatalog.com/hg/${repoName}/".length()
            index = pluginDescriptorFilePath.text.indexOf("http://buildsever.jcatalog.com/hg/${repoName}/")
        }
        if (index == -1) {
            log.info "'def scm = [url: \"http://buildsever.jcatalog.com/hg/${repoName}/\"]' this value didn't found in plugin description"
            log.info "You should change add this value to grails plugin descriptor manually:"
            println "def scm = [url: \"git@github.com:OpusCapita/${repoName}.git\"]"
            println "press enter to continue when this should be done"
            System.in.read()
        } else {
            log.info "updating [scm] link in ${pluginDescriptorFilePath.name}"
            String oldText = pluginDescriptorFilePath.text
            String text = oldText.substring(0, index) + "git@github.com:OpusCapita/${repoName}.git" + oldText.substring(index + length)
            pluginDescriptorFilePath.text = text

            log.info "git add ${pluginDescriptorFilePath.name}"
            executeCommand("git add ${pluginDescriptorFilePath.name}", gitRepoDir)
        }

        File readmeFile = new File(gitRepoDir, 'README.md')
        if (!readmeFile.exists()) {
            readmeFile.createNewFile()

            String descriptorText = pluginDescriptorFilePath.text

            def m = descriptorText =~ /title\s?=\s?"([^"]+)"/
            def title = m ? m[0][1] : ''
            if (!title) {
                m = descriptorText =~ /title\s?=\s?'([^']+)'/
                title = m ? m[0][1] : 'Grails Plugin'
            }

            m = descriptorText =~ /description\s?=\s?"([^"]+)"/
            def description = m ? m[0][1] : ''
            if (!description) {
                m = descriptorText =~ /description\s?=\s?'([^']+)'/
                description = m ? m[0][1] : ''
            }

            readmeFile.text = """# ${title}

${description}
"""

            executeCommand('git add README.md', gitRepoDir)
        }
    }
    if (new File(gitRepoDir, "CHANGES.txt").exists()) {
        executeCommand('git mv CHANGES.txt CHANGELOG.md', gitRepoDir)
    } else {
        executeCommand('touch CHANGELOG.md', gitRepoDir)
        executeCommand('hg add CHANGELOG.md', gitRepoDir)
    }
    executeCommand(['git', 'commit', '-am Moving from Mercurial to GIT'], gitRepoDir)
}

def migrateGrailsPluginFromMercurial2Git = { String url ->
    log.info '----------------------------------------------------------------'

    log.info "Migrate mercurial repo ${url}"

    if (!new File(gitRootDir, "fast-export").exists()) {
        executeCommand("git clone https://github.com/frej/fast-export", basedir)
    }

    String repoName = url.substring(url.lastIndexOf('/') + 1)

    File hgRepoDir = new File(hgRootDir, repoName)
    if (hgRepoDir.exists()) {
        log.warn "delete dir ${hgRepoDir.absolutePath}"
        hgRepoDir.deleteDir()
    }

    if (hgRepoDir.exists()) {
        log.error "could not delete ${hgRepoDir.absolutePath}"
        return
    }
    executeCommand("hg clone ${url} hg/${repoName}", basedir)

    if (!new File(hgRepoDir, "application.properties").exists()) {
        log.error "Sources in repository ${url} doesn't exists"
        log.info "press enter to next"
        System.in.read()
        return
    }

    File gitRepoDir = new File(gitRootDir, repoName)

    if (gitRepoDir.exists()) {
        log.warn "delete dir ${gitRepoDir.absolutePath}"

        gitRepoDir.deleteDir()
    }

    if (gitRepoDir.exists()) {
        log.error "could not delete ${gitRepoDir.absolutePath}"
        return
    }

    executeCommand("git init ${repoName}", gitRootDir)
    executeCommand("${basedir.absolutePath}/fast-export/hg-fast-export.sh -r ${hgRepoDir.absolutePath}", gitRepoDir)
    executeCommand("git checkout HEAD", gitRepoDir)
    if (new File(gitRepoDir, "application.properties").exists()) {
        changeLocalGitFiles(repoName, url, gitRepoDir)
    } else {
        log.warn "Skipping local changes for branch [master], because application.properties doesn't exists"
    }

    List branches = getMercurialBranches(hgRepoDir)

    log.info "found mercurial branches ${branches}"

    //migrated branches
    List mBranches = []
    if (branches.contains("8.0.GA.DEV")) {
        mBranches << "8.0.GA.DEV"
    }
    mBranches.addAll(
            branches.findAll { String branch ->
                branch.startsWith("bugfix-") && isActiveMercurialBranchByLastYear(branch, hgRepoDir)
            }
    )

    log.info "found active branches ${mBranches} in GIT"

    for (branchName in mBranches) {
        log.info "git checkout ${branchName}"

        executeCommand("git checkout ${branchName}", gitRepoDir)
        if (new File(gitRepoDir, "application.properties").exists()) {
            changeLocalGitFiles(repoName, url, gitRepoDir)
        } else {
            log.warn "Skipping local changes for branch [${branchName}], because application.properties doesn't exists"
        }
    }

    log.info "git checkout master"
    executeCommand("git checkout master", gitRepoDir)

    if (forcePushChanges) {
        log.info "create repo https://github.com/OpusCapita/${repoName}"
        executeCommand([
                '/bin/sh',
                '-c',
                "curl -u ${gitHubUser}:${gitHubPassword} -d '{\"name\": \"${repoName}\", \"description\": \"\", \"private\": true}' https://api.github.com/orgs/OpusCapita/repos"
        ], basedir)

        executeCommand("git remote add origin git@github.com:OpusCapita/${repoName}.git", gitRepoDir)
        executeCommand("git push -u origin master", gitRepoDir)
        executeCommand("git push --all", gitRepoDir)
        executeCommand("git push --tags", gitRepoDir)

        for (e in collaborators) {
            log.info "add collaborator [${e.key}] with [${e.value}] role for repo https://github.com/OpusCapita/${repoName}"
            executeCommand([
                    '/bin/sh',
                    '-c',
                    "curl -u ${gitHubUser}:${gitHubPassword} -X PUT -d '{\"role\": \"${e.value}\"}' https://api.github.com/repos/OpusCapita/${repoName}/collaborators/${e.key}"
            ], basedir)
        }
    }
    executeCommand([
            '/bin/sh',
            '-c',
            'hg rm *'
    ], hgRepoDir)

    def whereIs = new File(hgRepoDir, 'WHERE-IS.md')
    whereIs.createNewFile()
    whereIs.text = "REPOSITORY MOVED TO https://github.com/OpusCapita/${repoName}"

    executeCommand("hg add WHERE-IS.md", hgRepoDir)
    executeCommand(['hg', 'commit', '--close-branch', '-m Moving from Mercurial to GIT and close branch'], hgRepoDir)
    if (forcePushChanges) {
        executeCommand('hg push', hgRepoDir)
    }
    executeCommand([
            '/bin/sh',
            '-c',
            "echo \"${url}\" >> grails-plugins-black-list.txt"
    ], basedir)



    if (!forcePushChanges) {
        log.info "done, then you should to do several steps:"
        log.info "1. Create repository https://github.com/OpusCapita/${repoName}"
        log.info "2. cd ${gitRepoDir.absolutePath}"
        log.info "3. git remote add origin git@github.com:OpusCapita/${repoName}.git"
        log.info "4. git push -u origin master"
        log.info "5. git push --all"
        log.info "6. git push --tags"
        log.info "7. cd ${hgRepoDir.absolutePath}"
        log.info "8. hg push"
    } else {
        log.info "done"
    }

    log.info "press enter to next"
    System.in.read()
}

def blackListGrailsPlugins = new File("./grails-plugins-black-list.txt").readLines().collect {it.trim()}.findAll {it}
def grailsPlugins = new File("./grails-plugins-list.txt").readLines().collect {it.trim()}.findAll {it}
(grailsPlugins - blackListGrailsPlugins).forEach {
    migrateGrailsPluginFromMercurial2Git(it)
}