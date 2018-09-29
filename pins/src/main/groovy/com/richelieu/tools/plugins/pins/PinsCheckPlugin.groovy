package com.richelieu.tools.plugins.pins

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.richelieu.tools.plugins.pins.check.CodeChecker
import com.richelieu.tools.plugins.pins.logger.Logger
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * pins check plugins
 *
 * @author richelieu  09.21 2018
 */
class PinsCheckPlugin implements Plugin<Project> {

    static final String TAG = "PinsCheckPlugin"
    Project project
    Map<String, List<String>> pinsMappingDependencies

    /**
     * entrance method
     *
     * @param project which apply this plugin.
     */
    void apply(Project project) {
        Logger.initialize(project)
        Logger.info(TAG, "apply", "with: project = " + project + "")
        this.project = project

        project.afterEvaluate {
            Logger.info(TAG, "apply", "afterEvaluate")
            pinsMappingDependencies = PinsPlugin.pinsMappingDependencies

            def taskNamePrefix
            TestedExtension extension = (TestedExtension) project.extensions.getByName("android")
            if (extension instanceof LibraryExtension) {
                taskNamePrefix = 'package'
            } else {
                taskNamePrefix = 'merge'
            }
            //at least two build types: debug and release
            extension.buildTypes.each {
                def buildType = it.name
                if (extension.productFlavors.size() == 0) {
                    checkClassesAndResources(taskNamePrefix, buildType, null)
                } else {
                    extension.productFlavors.each {
                        checkClassesAndResources(taskNamePrefix, buildType, it.name)
                    }
                }
            }
        }
    }

    /**
     * find opportunity to check specified resources and class
     *
     * @param taskPrefix task prefix
     * @param buildType build type
     * @param productFlavor product flavor
     */
    def checkClassesAndResources(taskPrefix, buildType, productFlavor) {
        Logger.info(TAG, "checkClassesAndResources", "with: taskPrefix = " + taskPrefix + ", buildType = " + buildType + ", productFlavor = " + productFlavor + "")
        CodeChecker codeChecker

        //upper first letter for task name
        def buildTypeFirstUp = upperCase(buildType)
        def productFlavorFirstUp = productFlavor != null ? upperCase(productFlavor) : ""

        //assemble resource generate task name, for example: packageDebugResources for library module or mergeDebugResources for application module
        def mergeResourcesTaskName = taskPrefix + productFlavorFirstUp + buildTypeFirstUp + "Resources"
        def packageResourcesTask = project.tasks.findByName(mergeResourcesTaskName)

        //find opportunity to check resource
        if (packageResourcesTask != null) {
            if (codeChecker == null) {
                codeChecker = new CodeChecker(project, pinsMappingDependencies)
            }
            packageResourcesTask.doLast {
                codeChecker.checkResources(mergeResourcesTaskName)
            }
        }

        //assemble class generate task name, for example: compileDebugJavaWithJavac or compileReleaseJavaWithJavac
        def compileJavaTaskName = "compile${productFlavorFirstUp}${buildTypeFirstUp}JavaWithJavac"
        def compileJavaTask = project.tasks.findByName(compileJavaTaskName)

        //find opportunity to check dependencies
        if (compileJavaTask != null) {
            compileJavaTask.doLast {
                if (codeChecker == null) {
                    codeChecker = new CodeChecker(project, pinsMappingDependencies)
                }
                //merge product flavor and build type, for example Auto/Debug
                def productFlavorBuildType = productFlavor != null ? (productFlavor + File.separator + buildType) : buildType
                codeChecker.checkClasses(productFlavorBuildType, mergeResourcesTaskName)
            }
        }
    }

    static def upperCase(String str) {
        char[] ch = str.toCharArray()
        if (ch.size() == 0) return str

        if (ch[0] >= 'a' && ch[0] <= 'z') {
            ch[0] -= 32
        }
        return String.valueOf(ch)
    }

}
