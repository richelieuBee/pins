package com.richelieu.tools.plugins.pins.check

import com.richelieu.tools.plugins.pins.extension.DefaultPinsExtension
import com.richelieu.tools.plugins.pins.logger.Logger
import com.richelieu.tools.plugins.pins.manifest.AndroidManifest
import com.richelieu.tools.plugins.pins.manifest.CodeCheckManifest
import com.richelieu.tools.plugins.pins.resource.RecordItem
import com.richelieu.tools.plugins.pins.resource.MergedReport
import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * dependencies checker
 *
 * @author richelieu  09.21 2018
 */
class CodeChecker {

    static final String TAG = "CodeChecker"
    Project project
    String projectPath
    File buildDir
    DefaultPinsExtension pinsExtension

    CodeCheckManifest codeCheckManifest
    MergedReport mergedReport

    Map<String, List<String>> pinsMappingDependencies

    String errorMessage = ""
    String lineSeparator = System.getProperty("line.separator")

    CodeChecker(Project project, Map<String, List<String>> pinsMappingDependencies) {
        this.project = project
        this.pinsMappingDependencies = pinsMappingDependencies
        projectPath = project.projectDir.absolutePath
        buildDir = new File(project.projectDir, "build")

        pinsExtension = project.extensions.getByName("pins")

        codeCheckManifest = getCheckManifest()
    }

    //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>checkResources

    /**
     * check modified resources
     *
     * @param mergedReportFolderName "build/intermediates/incremental/${mergedReportFolderName}/merger.xml"
     */
    @TaskAction
    void checkResources(String mergedReportFolderName) {
        Logger.println()
        Logger.printLine(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>checkResources>>>>>>>>>>>>>>>>>>>>>>>>>>")
        Logger.info(TAG, "checkResources", "with: mergeTaskName = " + mergedReportFolderName + "")

        mergedReport = new MergedReport()
        boolean isExists = mergedReport.load(project.projectDir, mergedReportFolderName)
        if (!isExists) {
            Logger.info(TAG, "checkResources", "resourcesMergerFile is not exists!")
            return
        }

        //get all elements under dataSet-config:main-source from "build/intermediates/incremental/${mergedReportFolderName}/merger.xml"
        NodeList resourcesNodeList = mergedReport.getResourcesNodeList()
        //filter modified resource list, only new record or record time out item will be contains
        List<File> modifiedResourcesList = getModifiedResourcesList(resourcesNodeList)
        if (modifiedResourcesList.size() == 0) {//no modified item
            Logger.info(TAG, "checkResources", "nothing be modified.")
            updateCheckManifest()
            return
        }

        checkModifiedResources(modifiedResourcesList)
        if (errorMessage != "") {
            throw new GradleScriptException(errorMessage, null)
        }
        String packageName = getSillManifest().getPackageName()
        codeCheckManifest.packageName = packageName
        updateCheckManifest()
    }

    /**
     * get modified resource by matches merged report(Android maintain) and modified record(pins maintain)
     *
     * @param lstOfMergedReport merge report of this packageResources/mergeResources maintains
     */
    List<File> getModifiedResourcesList(NodeList lstOfMergedReport) {
        Logger.info(TAG, "getModifiedResourcesList", "with: lstOfMergedReport = " + lstOfMergedReport + "")

        //load all modified resource
        Map<String, RecordItem> pathMappingRecord = codeCheckManifest.getModifiedResourceRecord()
        List<File> lstOfModifiedResourceFile = new ArrayList<>()
        if (lstOfMergedReport == null || lstOfMergedReport.length == 0) {
            return lstOfModifiedResourceFile
        }

        //read reports source element, match with record to find which modified.
        for (int sourceIndex = 0; sourceIndex < lstOfMergedReport.getLength(); sourceIndex++) {
            Element resourcesElement = (Element) lstOfMergedReport.item(sourceIndex)
            NodeList fileNodeList = resourcesElement.getElementsByTagName("file")

            //read file element
            for (int fileIndex = 0; fileIndex < fileNodeList.getLength(); fileIndex++) {
                Element fileElement = (Element) fileNodeList.item(fileIndex)
                String filePath = fileElement.getAttribute("path")

                //only xml resource
                if (filePath != null && filePath.endsWith(".xml")) {
                    File file = project.file(filePath)
                    //get timestamp from file instance
                    def curTimestamp = file.lastModified()

                    //load record item from record map by key path
                    RecordItem recordItem = pathMappingRecord.get(filePath)
                    //Logger.info(TAG, "getModifiedResourcesList", "recordItem = ${recordItem}, curTimestamp = ${curTimestamp}")

                    if (recordItem == null || recordItem.lastModified.longValue() < curTimestamp) {
                        //only new record or record time out item will be added
                        lstOfModifiedResourceFile.add(file)

                        //update pathMappingRecord, point instance
                        if (recordItem == null) {
                            recordItem = new RecordItem()
                            recordItem.name = file.name
                            recordItem.path = filePath
                            recordItem.pinsName = getPinsProjectName(filePath)
                            pathMappingRecord.put(filePath, recordItem)
                        }
                        recordItem.lastModified = curTimestamp
                    }
                }
            }
        }

        Logger.info(TAG, "getModifiedResourcesList", "end: lstOfModifiedFile = " + lstOfModifiedResourceFile)
        return lstOfModifiedResourceFile
    }

    /**
     * check modified resource by match reference
     *
     * @param lstOfModifiedResources be modified resource list
     */
    void checkModifiedResources(List<File> lstOfModifiedResources) {
        Logger.info(TAG, "checkModifiedResources", "with: lstOfModifiedResources = " + lstOfModifiedResources + "")

        //get name mapping pins-project name map from report
        Map<String, String> nameMappingPinsName = mergedReport.getResourcesMap()
        def resourcesPattern = /@(dimen|drawable|color|string|style|id|mipmap|layout)\/[A-Za-z0-9_]+/
        lstOfModifiedResources.each {
            Logger.info(TAG, "checkModifiedResources", "modified resources: " + it)

            //read modified xml resource line by line, and check if it has some reference
            String text = it.text
            List<String> textLines = text.readLines()
            def matcher = (text =~ resourcesPattern)
            def absolutePath = it.absolutePath
            def pinsName = getPinsProjectName(absolutePath)
            Logger.info(TAG, "checkModifiedResources", "absolutePath = ${absolutePath}, pinsName = ${pinsName}")
            //Logger.printLine(text) //too much info

            while (matcher.find()) {// has reference code
                def find = matcher.group()
                def name = find.substring(find.indexOf("/") + 1)
                def from = nameMappingPinsName.get(name)
                Logger.info(TAG, "checkModifiedResources", "resource changed: find = ${find}, name = ${name}, from = ${from}")

                //from not null, not same pins-project, from is not dependent pins-project by pinsName
                if (from != null && pinsName != from && !isReference(pinsName, from)) {
                    //assemble error info
                    List<Number> lstOfMatchLine = textLines.findIndexValues {
                        it.contains(find)
                    }
                    lstOfMatchLine.each {
                        def lineIndex = it.intValue()
                        def lineContext = textLines.get(lineIndex).trim()

                        //recheck, if match text is comment, recheck failed
                        if (lineContext.startsWith("<!--")) {
                            return
                        }

                        def message = lineSeparator + absolutePath + ':' + (lineIndex + 1)
                        if (!errorMessage.contains(message)) {
                            message += lineSeparator
                            message += "WrongDependencyException:" + lineSeparator
                            message += "'${pinsName}' can't use [${find}] which from pinProject '${from}'."
                            message += lineSeparator
                            errorMessage += message
                        }
                    }
                }
            }
        }
    }

    //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>checkClasses

    @TaskAction
    void checkClasses(String productFlavorBuildType, mergedReportFolderName) {
        Logger.println()
        Logger.printLine(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>checkClasses>>>>>>>>>>>>>>>>>>>>>>>>>>")
        Logger.info(TAG, "checkClasses", "with: productFlavorBuildType = " + productFlavorBuildType + ", mergedReportFolderName = " + mergedReportFolderName + "")

        //if sill hasn't any class, skip this check  TODO: should check sill and all pins, then skip
        File sillClassDir = new File(buildDir, "intermediates/classes/${productFlavorBuildType}/" + codeCheckManifest.packageName.replace(".", "/"))
        if (!sillClassDir.exists()) {
            Logger.info(TAG, "checkClasses", "sillClassDir is not exists!")
            return
        }

        //load all modified java
        List<File> modifiedClassesList = getModifiedClassesList()
        if (modifiedClassesList.size() == 0) {
            Logger.info(TAG, "checkClasses", "nothing be modified.")
            return
        }

        //any point?
        if (mergedReport == null) {
            mergedReport = new MergedReport()
            mergedReport.load(project.projectDir, mergedReportFolderName)
            if (!mergedReport.resourcesMergerFile.exists()) {
                Logger.info(TAG, "checkClasses", "${mergedReportFolderName} is not exists!")
                return
            }
        }

        checkModifiedClasses(modifiedClassesList)
        if (errorMessage != "") {
            throw new GradleScriptException(errorMessage, null)
        }
        updateCheckManifest()
    }

    /**
     * get modified class.
     */
    List<File> getModifiedClassesList() {
        Logger.info(TAG, "getModifiedClassesList", "enter method")

        //read modified class from code-check-manifest.xml
        Map<String, RecordItem> pathMappingRecord = codeCheckManifest.getModifiedClassRecord()

        List<File> lstOfModifiedClassFile = new ArrayList<>()
        File sillSourceDir = new File(pinsExtension.sillProject.pinProjectDir, "/src/main/java")

        //load modified java file on sill-project and all pins-project
        getModifiedJavaFile(sillSourceDir, lstOfModifiedClassFile, pathMappingRecord)
        pinsExtension.lstOfPinedProject.each {
            Logger.info(TAG, "getModifiedClassesList", "pinsExtension.lstOfPinedProject.each: " + it)

            sillSourceDir = new File(it.pinProjectDir, "/src/main/java")
            getModifiedJavaFile(sillSourceDir, lstOfModifiedClassFile, pathMappingRecord)
        }

        return lstOfModifiedClassFile
    }

    /**
     * get modified java file by matches sill source java and modified record(pins maintain).
     *
     * @param sillSourceDir sill source java folder
     * @param lstOfModifiedClassFile modified class file list, output args
     * @param pathMappingRecord path mapping modified record map.
     */
    void getModifiedJavaFile(File sillSourceDir, List<File> lstOfModifiedClassFile, Map<String, RecordItem> pathMappingRecord) {
        Logger.info(TAG, "getModifiedJavaFile", "with: sillSourceDir = " + sillSourceDir + ", lstOfModifiedClassFile = " + lstOfModifiedClassFile + ", pathMappingRecord = " + pathMappingRecord + "")

        //list source code dir
        sillSourceDir.listFiles().each {
            if (it.isDirectory()) {//loop
                getModifiedJavaFile(it, lstOfModifiedClassFile, pathMappingRecord)
            } else {
                def currentModified = it.lastModified()
                RecordItem recordItem = pathMappingRecord.get(it.absolutePath)

                //check file modified timestamp
                if (recordItem == null || recordItem.lastModified.longValue() < currentModified) {
                    lstOfModifiedClassFile.add(it) //source file changed

                    if (recordItem == null) {
                        recordItem = new RecordItem()
                        recordItem.name = it.name
                        recordItem.path = it.absolutePath
                        recordItem.pinsName = getPinsProjectName(it.absolutePath)
                        pathMappingRecord.put(it.absolutePath, recordItem)
                    }
                    recordItem.lastModified = it.lastModified()
                }
            }
        }

        Logger.info(TAG, "getModifiedJavaFile", "end: " + lstOfModifiedClassFile)
    }

    /**
     * check modified class by match reference
     *
     * @param lstOfModifiedResources be modified resource list
     */
    void checkModifiedClasses(List<File> lstOfModifiedClass) {
        Logger.info(TAG, "checkModifiedClasses", "with: modifiedClassesList = " + lstOfModifiedClass + "")

        Map<String, String> nameMappingPinsName = mergedReport.getResourcesMap()

        Map<String, String> classMappingPinsName = new HashMap<>()
        codeCheckManifest.getModifiedClassRecord().each {
            RecordItem recordItem = it.value
            def path = recordItem.path
            def name = path.substring(path.indexOf("java") + 5, path.lastIndexOf(".")).replace(File.separator, ".")
            classMappingPinsName.put(name, recordItem.pinsName)
        }
        Logger.info(TAG, "checkModifiedClasses", "classMappingPinsName: " + classMappingPinsName)

        //match any import and R.xxxx.xxx reference code
        def resourcesPattern = /R.(dimen|drawable|color|string|style|id|mipmap|layout).[A-Za-z0-9_]+|import\s[A-Za-z0-9_.]+/
        lstOfModifiedClass.each {
            Logger.info(TAG, "checkModifiedClasses", "modified class: " + it)

            String text = it.text
            List<String> textLines = text.readLines()
            def matcher = (text =~ resourcesPattern)
            def absolutePath = it.absolutePath
            def pinsName = getPinsProjectName(absolutePath)
            Logger.info(TAG, "checkModifiedClasses", "absolutePath = ${absolutePath}, pinsName = ${pinsName}")
            //Logger.printLine(text) //too much info

            while (matcher.find()) {// has match source code
                def find = matcher.group()
                def from, name
                if (find.startsWith("R")) {// R.xxx.xxx
                    name = find.substring(find.lastIndexOf(".") + 1)
                    from = nameMappingPinsName.get(name)

                } else if (find.startsWith("import")) {//import xxx
                    name = find.substring(find.lastIndexOf(" ") + 1, find.length())
                    from = classMappingPinsName.get(name)

                }
                Logger.info(TAG, "checkModifiedClasses", "class changed: find = ${find}, name = ${name}, from = ${from}")

                //from not null, not same pins-project, from is not dependent pins-project by pinsName
                if (from != null && pinsName != from && !isReference(pinsName, from)) {

                    //assemble error info
                    List<Number> lines = textLines.findIndexValues {
                        it.contains(find)
                    }
                    lines.each {
                        def lineIndex = it.intValue()
                        def lineContext = textLines.get(lineIndex).trim()

                        //recheck, if match text is comment, recheck failed
                        if (lineContext.startsWith("//") || lineContext.startsWith("/*")) {
                            return
                        }

                        def message = lineSeparator + absolutePath + ':' + (lineIndex + 1)
                        if (!errorMessage.contains(message)) {
                            message += lineSeparator
                            message += "WrongDependencyException:" + lineSeparator
                            message += "'${pinsName}' can't use [${find}] which from pinProject '${from}'."
                            message += lineSeparator
                            errorMessage += message
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------

    /**
     * parse pins-project by absolute path
     *
     * @param absolutePath absolute path
     */
    String getPinsProjectName(absolutePath) {
        Logger.info(TAG, "getPinsProjectName", "with: absolutePath = " + absolutePath + "")

        String moduleName = absolutePath.replace(projectPath, "")
        moduleName = moduleName.substring(0, moduleName.indexOf(MergedReport.SRC))
        if (File.separator == "\\") {
            moduleName = moduleName.replaceAll("\\\\", ":")
        } else {
            moduleName = moduleName.replaceAll("/", ":")
        }
        return moduleName
    }

    /***
     * check dependency relationship
     *
     * @param pinsName source project name
     * @param from dependency project name
     */
    boolean isReference(String pinsName, String from) {
        Logger.info(TAG, "isReference", "with: pinsName = " + pinsName + ", from = " + from + "")

        List<String> original = new ArrayList<>()
        original.add(pinsName)
        return isReference(pinsName, from, original)
    }

    /**
     * check dependency relationship
     *
     * @param pinsName source project name
     * @param from dependency project name
     * @param lstOfPinsDependencies dependencies list of pins-project(include itself) which named pinsName
     */
    boolean isReference(String pinsName, String from, List<String> lstOfPinsDependencies) {
        Logger.info(TAG, "isReference", "with: pinsName = " + pinsName + ", from = " + from + ", original = " + lstOfPinsDependencies + "")
        Logger.info(TAG, "isReference", "pinsMappingDependencies: " + pinsMappingDependencies)

        //get pinsName project dependencies
        List<String> referenceList = pinsMappingDependencies.get(pinsName)
        if (referenceList == null) {
            return false
        }
        if (referenceList.contains(from)) {
            return true
        }

        //pinsName not dependent from, check if pinsName's dependencies dependent from
        for (int index = 0; index < referenceList.size(); index++) {
            //already contains current dependency
            if (lstOfPinsDependencies.contains(referenceList[index])) {
                continue
            } else {
                //add current dependency to list
                lstOfPinsDependencies.add(referenceList[index])
            }

            //then check
            if (isReference(referenceList[index], from, lstOfPinsDependencies)) {
                return true
            }
        }
        return false
    }

//    String getPinsModulePackageName(File directory, String packageName) {
//        if (directory == null) return packageName
//
//        File[] files = directory.listFiles()
//        if (files == null || files.length == 0) {
//            return packageName + "." + directory.name
//        } else if (files.length == 1) {
//            if (files[0].isFile()) {
//                return packageName + "." + directory.name
//            } else {
//                return getPinsModulePackageName(files[0], packageName + "." + directory.name)
//            }
//        } else {
//            for (int i = 0; i < files.size(); i++) {
//                if (files[i].isFile()) {
//                    return packageName + "." + directory.name
//                }
//            }
//        }
//    }

    /**
     * get sill-project AndroidManifest instance
     */
    private AndroidManifest getSillManifest() {
        AndroidManifest mainManifest = new AndroidManifest()
        def manifest = new File(pinsExtension.sillProject.pinProjectDir, "src/main/AndroidManifest.xml")
        mainManifest.load(manifest)
        return mainManifest
    }

    /**
     * get code check manifest from pins folder
     */
    private CodeCheckManifest getCheckManifest() {
        CodeCheckManifest codeCheckManifest = new CodeCheckManifest()
        codeCheckManifest.load(project.file("build/pins/code-check-manifest.xml"))
        return codeCheckManifest
    }

    /**
     * update code check manifest
     */
    private CodeCheckManifest updateCheckManifest() {
        if (codeCheckManifest == null) {
            codeCheckManifest = new CodeCheckManifest()
        }
        return codeCheckManifest.update(project.file("build/pins/code-check-manifest.xml"))
    }

}