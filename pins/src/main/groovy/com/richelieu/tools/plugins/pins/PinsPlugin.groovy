package com.richelieu.tools.plugins.pins

import com.android.build.gradle.BaseExtension
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.manifmerger.XmlDocument
import com.richelieu.tools.plugins.pins.manifest.AndroidManifest
import com.richelieu.tools.plugins.pins.extension.DefaultPinsExtension
import com.richelieu.tools.plugins.pins.logger.Logger
import com.richelieu.tools.plugins.pins.extension.PinsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * pins core plugins
 *
 * @author richelieu  09.21 2018
 */
class PinsPlugin implements Plugin<Project> {


    static final String TAG = "PinsPlugin"
    static final String RPath = "/build/generated/source/r/"
    static final String manifestPath = "/src/main/AndroidManifest.xml"

    Project project
    PinsProject curPinsProject
    DefaultPinsExtension pinsExtension
    boolean originSourceSetCleared

    /**
     * pins-project mapping it's pins dependencies.
     */
    public static Map<String, List<String>> pinsMappingDependencies

    /**
     * entrance method
     *
     * @param project which apply this plugin.
     */
    void apply(Project project) {
        Logger.initialize(project)
        Logger.printLine("pins version 1.0.0, enjoy yourself")
        Logger.info(TAG, "apply", "with: project = " + project + "")
        this.project = project

        pinsExtension = project.extensions.create(PinsExtension, "pins", DefaultPinsExtension, project)
        pinsExtension.onProjectAddedListener = new DefaultPinsExtension.OnProjectAddedListener() {
            @Override
            void onProjectAdd(PinsProject newProject, boolean isSill) {
                Logger.info(TAG, "onProjectAdd", "with: newProject = " + newProject + ", isSill = " + isSill + "")
                if (!originSourceSetCleared) {
                    clearSillProject()
                    if (!isSill) {
                        pinProject(pinsExtension.sillProject)
                    }
                    originSourceSetCleared = true
                }

                if (isSill) {
                    clearSillProject()
                    pinProject(pinsExtension.sillProject)
                    pinsExtension.lstOfPinedProject.each {
                        if (it.name == pinsExtension.sillProject.name) {
                            return
                        }
                        pinProject(it)
                    }
                } else {
                    pinProject(newProject)
                }
            }
        }

        //read pins-project  dependency relationship from dependencies configuration's method named pinProject  - richelieu
        project.dependencies.metaClass.pinProject { path ->
            Logger.info(TAG, "apply", "dependencies:pinProject:" + path)
            resolveDependencies(path)
            return []
        }

        project.afterEvaluate {
            Logger.println()
            Logger.printLine(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
            Logger.printLine(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> afterEvaluate >>>>>>>>>>>>>>>>>>>")
            Logger.printLine(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
            Logger.println()

            checkSillProject()
            pinsMappingDependencies = new HashMap<>()

            if (!originSourceSetCleared) {
                clearSillProject()
                pinProject(pinsExtension.sillProject)
                originSourceSetCleared = true
            }

            // apply pin-project build.gradle  - richelieu
            applyBuildGradle(pinsExtension.sillProject)
            List<PinsProject> lstOfPinsProject = pinsExtension.lstOfPinedProject.clone()
            lstOfPinsProject.each {
                if (it.name == pinsExtension.sillProject.name) {
                    return
                }
                applyBuildGradle(it)
            }

            checkDependenciesValidity()
            mergeSillAndroidManifest()

            project.tasks.preBuild.doFirst {
                Logger.info(TAG, "apply", "preBuild.doFirst")
                pinsMappingDependencies = new HashMap<>()
                pinAllProject()
                checkDependenciesValidity()
                mergeSillAndroidManifest()
                generateR()
            }
        }
    }

    /**
     * check sill project, create default one if no exists.
     */
    def checkSillProject() {
        Logger.info(TAG, "checkSillProject", "enter method")

        if (pinsExtension.sillProject == null) {
            pinsExtension.sillProject = new PinsProject()
            def name = ":main"
            def pinDir = new File(project.projectDir, "/main")
            if (!pinDir.exists()) {
                throw new GradleException("can't find specified sill-project [${name}] under path [${pinDir.absolutePath}].")
            }
            pinsExtension.sillProject.name = name
            pinsExtension.sillProject.pinProjectDir = pinDir
        }
    }

    /**
     * merge AndroidManifest.xml base on sill's one.
     */
    def mergeSillAndroidManifest() {
        Logger.info(TAG, "mergeSillAndroidManifest", "enter method")

        //create invoker base on sill project's AndroidManifest.xml
        Logger logger = new Logger();
        File mainManifestFile = new File(pinsExtension.sillProject.pinProjectDir, manifestPath)
        ManifestMerger2.MergeType mergeType = ManifestMerger2.MergeType.APPLICATION
        XmlDocument.Type documentType = XmlDocument.Type.MAIN
        ManifestMerger2.Invoker invoker = new ManifestMerger2.Invoker(mainManifestFile, logger, mergeType, documentType)
        invoker.withFeatures(ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)

        //add all pins-project excepts sill project
        pinsExtension.lstOfPinedProject.each {
            if (it.name == pinsExtension.sillProject.name) {
                return
            }

            //find manifest file
            def pinManifestFile = new File(it.pinProjectDir, manifestPath)
            if (pinManifestFile.exists()) {
                invoker.addLibraryManifest(pinManifestFile)
            }
        }

        //do merge
        def mergingReport = invoker.merge()
        if (!mergingReport.result.success) {
            mergingReport.log(logger)
            throw new GradleException(mergingReport.reportString)
        }

        //export merged content
        def moduleAndroidManifest = mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
        moduleAndroidManifest = new String(moduleAndroidManifest.getBytes("UTF-8"))

        //create specified path
        def exportDir = new File(project.projectDir, "build/pins/main")
        exportDir.mkdirs()

        //write to specified path
        def AndroidManifestFile = new File(exportDir, "AndroidManifest.xml")
        AndroidManifestFile.createNewFile()
        AndroidManifestFile.write(moduleAndroidManifest)

        //set source sets's manifest path to specified path
        def extensionContainer = project.getExtensions()
        BaseExtension android = extensionContainer.getByName('android')
        android.sourceSets.main.manifest.srcFile project.projectDir.absolutePath + "/build/pins/main/AndroidManifest.xml"
    }

    /**
     * pin all pins-project
     */
    def pinAllProject() {
        Logger.info(TAG, "pinAllProject", "enter method")

        clearProject("main")
        pinProject(pinsExtension.sillProject)
        pinsExtension.lstOfPinedProject.each {
            if (it.name == pinsExtension.sillProject.name) {
                return
            }
            pinProject(it)
        }
    }

    /**
     * pin pins-project all type source set.
     *
     * @param pinsProject pins-project
     */
    def pinProject(PinsProject pinsProject) {
        Logger.info(TAG, "pinProject", "with: pinsProject = " + pinsProject + "")

        pinProjectByType(pinsProject, "main")
        pinProjectByType(pinsProject, "androidTest")
        pinProjectByType(pinsProject, "test")
    }

    /**
     * pin pins-project config (java config\res config\jni config\aidl config and so on)
     *
     * @param pinsProject pins-project instance
     * @param type sources set type
     */
    def pinProjectByType(PinsProject pinsProject, def type) {
        Logger.info(TAG, "pinProjectByType", "with: pinsProject = " + pinsProject + ", type = " + type + "")

        def absolutePath = pinsProject.pinProjectDir.absolutePath
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.getByName(type)
        obj.java.srcDir(absolutePath + "/src/${type}/java")
        obj.res.srcDir(absolutePath + "/src/${type}/res")
        obj.jni.srcDir(absolutePath + "/src/${type}/jni")
        obj.jniLibs.srcDir(absolutePath + "/src/${type}/jniLibs")
        obj.aidl.srcDir(absolutePath + "/src/${type}/aidl")
        obj.assets.srcDir(absolutePath + "/src/${type}/assets")
        obj.shaders.srcDir(absolutePath + "/src/${type}/shaders")
        obj.resources.srcDir(absolutePath + "/src/${type}/resources")
        obj.renderscript.srcDir(absolutePath + "/src/${type}/rs")
    }

    /**
     * clear sill project
     */
    def clearSillProject() {
        Logger.info(TAG, "clearSillProject", "enter method")

        clearProject("main")
        clearProject("androidTest")
        clearProject("test")
    }

    /**
     * clear real project configs
     *
     * @param type source sets type
     */
    def clearProject(def type) {
        Logger.info(TAG, "clearProject", "with: type = " + type + "")

        def srcDirs = []
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.getByName(type)
        obj.java.srcDirs = srcDirs
        obj.res.srcDirs = srcDirs
        obj.jni.srcDirs = srcDirs
        obj.jniLibs.srcDirs = srcDirs
        obj.aidl.srcDirs = srcDirs
        obj.assets.srcDirs = srcDirs
        obj.shaders.srcDirs = srcDirs
        obj.resources.srcDirs = srcDirs
        obj.renderscript.srcDirs = srcDirs
    }

    /**
     * check all pins-project's dependencies are valid or not. 
     */
    def checkDependenciesValidity() {
        Logger.info(TAG, "checkDependenciesValidity", "enter method")

        //check sill project first
        checkDependenciesValidity(pinsExtension.sillProject)
        pinsExtension.lstOfPinedProject.each {
            if (it.name == pinsExtension.sillProject.name) {
                return
            }
            checkDependenciesValidity(it)
        }
    }

    /**
     * check specified pins-project's dependencies are valid or not
     */
    def checkDependenciesValidity(PinsProject pinsProject) {
        Logger.info(TAG, "checkDependenciesValidity", "with: pinsProject = " + pinsProject + "")

        //get all dependencies of specified name
        List<String> lstOfDependencies = pinsMappingDependencies.get(pinsProject.name)
        //no dependency, no need to check
        if (lstOfDependencies == null) {
            return
        }

        for (String path : lstOfDependencies) {
            isDependentProjectInclude(pinsProject, path)
        }
    }

    /**
     * check specified pins-project and it's dependency are in the same module or not.
     *
     * @param pinsProject specified pins-project
     * @param dependencyPath dependency path
     */
    def isDependentProjectInclude(PinsProject pinsProject, String dependencyPath) {
        Logger.info(TAG, "isDependentProjectInclude", "with: pinsProject = " + pinsProject + ", path = " + dependencyPath + "")

        //get dependent pins-project
        PinsProject dependentPinsProject = pinsExtension.buildPinProject(dependencyPath)
        if (dependentPinsProject == null) {// wrong dependency
            throw new GradleException("can't find specified pins-project '${dependencyPath}', which is referenced by pinsProject${pinsProject.name}")
        }

        //if dependentPinsProject is in this module
        boolean include = false
        pinsExtension.lstOfPinedProject.each {
            if (it.name == dependentPinsProject.name) {
                include = true
            }
        }

        //if dependentPinsProject is sill project of this module
        if (pinsExtension.sillProject.name == dependentPinsProject.name) {
            include = true
        }

        //dependent pins-project exits bud not pined.
        if (!include) {
            throw new GradleException("pinsProject${dependentPinsProject.name} is referenced by pinsProject${pinsProject.name}, but it's not pined.")
        }
    }

    /**
     * generate R.java for each pins-project
     */
    def generateR() {
        Logger.info(TAG, "generateR", "enter method")

        def pinManifestFile = new File(pinsExtension.sillProject.pinProjectDir, manifestPath)
        def packageName = getPackageName(pinManifestFile)
        BaseExtension extension = (BaseExtension) project.extensions.getByName("android")

        //at least two build type: debug and release
        extension.buildTypes.each {
            def buildType = it.name
            if (extension.productFlavors.size() == 0) {
                generateRByProductFlavorBuildType(packageName, buildType, null)
            } else {
                extension.productFlavors.each {
                    Logger.info(TAG, "extension.productFlavors: %s", it)
                    generateRByProductFlavorBuildType(packageName, buildType, it.name)
                }
            }
        }
    }

    /**
     * generate R.java for specified flavor and build type if someone has res folder, otherwise generate nothing.
     *
     * @param sillPackageName sill project package name
     * @param buildType build type
     * @param productFlavor produce flavor
     */
    def generateRByProductFlavorBuildType(sillPackageName, buildType, productFlavor) {
        Logger.info(TAG, "generateRByProductFlavorBuildType", "with: mainPackageName = " + sillPackageName + ", buildType = " + buildType + ", productFlavor = " + productFlavor + "")

        //assemble process resource task name,ex processDebugResources
        def buildTypeFirstUp = upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? upperCase(productFlavor) : ""
        def processResourcesTaskName = "process${productFlavorFirstUp}${buildTypeFirstUp}Resources"
        def processResourcesTask = project.tasks.findByName(processResourcesTaskName)
        def productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType

        //find opportunity to generate R.java
        if (processResourcesTask != null) {
            processResourcesTask.doLast {
                if (rewriteR(sillPackageName, buildType, productFlavor)) {
                    generateSubR(sillPackageName, productFlavorBuildType)
                }

            }
        } else {// use generateRFile task instead
            def generateRFileTaskName = "generate${productFlavorFirstUp}${buildTypeFirstUp}RFile"
            def generateRFileTask = project.tasks.findByName(generateRFileTaskName)
            if (generateRFileTask != null) {
                generateRFileTask.doLast {
                    if (rewriteR(sillPackageName, buildType, productFlavor)) {
                        generateSubR(sillPackageName, productFlavorBuildType)
                    }

                }
            }
        }
        Logger.info(TAG, "generateRByProductFlavorBuildType", "processResourcesTask: " + processResourcesTask)
    }

    /**
     * find native R.java and delete it's final key word.
     *
     * @param sillPackageName sill project package name
     * @param buildType build type
     * @param productFlavor produce flavor
     */
    boolean rewriteR(sillPackageName, buildType, productFlavor) {
        Logger.info(TAG, "rewriteR", "with: sillPackageName = " + sillPackageName + ", buildType = " + buildType + ", productFlavor = " + productFlavor + "")

        try {
            //find native R.java and delete it's final key word.
            String productFlavorBuildType = productFlavor != null ? (productFlavor + "/" + buildType) : buildType
            String path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/" + sillPackageName.replace(".", "/") + "/R.java"
            File file = project.file(path)
            String newR = file.text.replace("public final class R", "public class R")
            file.write(newR)//rewrite native R.java

            return true
        } catch (FileNotFoundException ignored) {
            Logger.info(TAG, "generateRByProductFlavorBuildType", "no pins-project has res folder.")
        }
        return false
    }

    /**
     * generate empty R.java which extends sill project's R.java for all pined pins-project
     *
     * @param sillPackageName sill project package name
     * @param productFlavorBuildType produce flavor and build type
     */
    def generateSubR(sillPackageName, productFlavorBuildType) {
        Logger.info(TAG, "generateSubR", "with: packageName = " + sillPackageName + ", productFlavorBuildType = " + productFlavorBuildType + "")

        def packageNames = []
        //find all pined pins-project of this module
        pinsExtension.lstOfPinedProject.each {
            //get pins-project main package name
            def pinManifestFile = new File(it.pinProjectDir, manifestPath)
            if (!pinManifestFile.exists()) {
                return
            }
            def pinModulePackageName = getPackageName(pinManifestFile)
            if (pinModulePackageName == null || packageNames.contains(pinModulePackageName)) {
                return
            }
            //same to List.add(Element)
            packageNames << pinModulePackageName

            //assemble R.java path
            def path = project.projectDir.absolutePath + RPath + productFlavorBuildType + "/" + pinModulePackageName.replace(".", "/")
            File file = project.file(path + "/R.java")
            if (project.file(path).exists()) {
                return
            }

            //generate R.java by extend native R.java
            project.file(path).mkdirs()
            file.write("package " + pinModulePackageName + ";\n\n/** This class is generated by pins plugin, DO NOT MODIFY. */\npublic class R extends " + sillPackageName + ".R {\n\n}")

            //logging it
            Logger.info(TAG, "generateSubR", "${it.name} generate " + pinModulePackageName + '.R.java')
        }
    }

    /**
     * apply build.gradle of specify pin project.
     *
     * @param pinsProject pins project which build.gradle from.
     */
    void applyBuildGradle(PinsProject pinsProject) {
        Logger.info(TAG, "applyBuildGradle", "with: pinsProject = " + pinsProject + "")

        def buildGradleFile = new File(pinsProject.pinProjectDir, "build.gradle")
        if (buildGradleFile.exists()) {
            curPinsProject = pinsProject
            project.apply from: buildGradleFile.absolutePath
            Logger.info(TAG, "applyBuildGradle", "apply " + buildGradleFile.absolutePath)
        }
    }

    /**
     * resolve current project's pins-project dependencies, so that code check can be work.
     *
     * @param path pins-project path
     */
    def resolveDependencies(String path) {
        Logger.info(TAG, "resolveDependencies", "with: path = " + path + ", curPinsProject.name = " + curPinsProject.name)

        if (pinsExtension == null || curPinsProject == null) {
            return
        }

        //find pins-project by path in pins closure
        PinsProject pinsProject = pinsExtension.buildPinProject(path)
        if (pinsProject == null) {
            throw new GradleException("can't find specified pins-project '${path}', which is referenced by pinsProject${curPinsProject.name}")
        }

        List<String> referenceList = pinsMappingDependencies.get(curPinsProject.name)
        if (referenceList == null) {
            referenceList = new ArrayList<>()
            referenceList.add(pinsProject.name)
            pinsMappingDependencies.put(curPinsProject.name, referenceList)
        } else {
            if (!referenceList.contains(pinsProject.name)) {
                referenceList.add(pinsProject.name)
            }
        }
        Logger.info(TAG, "resolveDependencies", "pinsMappingDependencies: " + pinsMappingDependencies)
    }

    static def upperCase(String str) {
        char[] ch = str.toCharArray()
        if (ch[0] >= 'a' && ch[0] <= 'z') {
            ch[0] -= 32
        }

        return String.valueOf(ch)
    }


    static def getPackageName(File androidManifestFile) {
        AndroidManifest androidManifest = new AndroidManifest()
        androidManifest.load(androidManifestFile)
        return androidManifest.packageName
    }

}