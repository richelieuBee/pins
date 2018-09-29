package com.richelieu.tools.plugins.pins.resource

import com.richelieu.tools.plugins.pins.logger.Logger
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory


/**
 * "build/intermediates/incremental/${mergedReportFolderName}/merger.xml" instance
 *
 * @author richelieu 09.28 2018
 */
class MergedReport {
    static final TAG = "MergedReport"
    static String SRC = File.separator + "src" + File.separator

    String projectPath
    File resourcesMergerFile
    NodeList resourcesNodeList
    Map<String, String> nameMappingPinsName

    /**
     * load "build/intermediates/incremental/${mergedReportFolderName}/merger.xml" file content
     *
     * @param projectDir project dir
     * @param mergedReportFolderName task that do merge
     */
    boolean load(File projectDir, String mergedReportFolderName) {
        projectPath = projectDir.absolutePath
        String mergedPath = "build/intermediates/incremental/${mergedReportFolderName}/merger.xml"
        resourcesMergerFile = new File(projectDir, mergedPath)
        return resourcesMergerFile.exists()
    }

    /**
     * get all elements under dataSet-config:main-source from merger.xml
     */
    NodeList getResourcesNodeList() {
        Logger.info(TAG, "getResourcesNodeList", "enter method:" + resourcesNodeList)
        if (resourcesNodeList != null) {
            return resourcesNodeList
        }

        //read merger.xml content
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        DocumentBuilder builder = factory.newDocumentBuilder()

        FileInputStream inputStream = new FileInputStream(resourcesMergerFile)
        Document doc = builder.parse(inputStream)
        Element rootElement = doc.getDocumentElement()

        //read all dataSet-config:main-source elements
        NodeList dataSetNodeList = rootElement.getElementsByTagName("dataSet")
        for (int i = 0; i < dataSetNodeList.getLength(); i++) {
            Element dataSetElement = (Element) dataSetNodeList.item(i)
            def config = dataSetElement.getAttribute("config")
            if (config == "main") {
                resourcesNodeList = dataSetElement.getElementsByTagName("source")
                Logger.info(TAG, "getResourcesNodeList", "exit method: " + resourcesNodeList)
                return resourcesNodeList
            }
        }
        return null
    }

    Map<String, String> getResourcesMap() {
        Logger.info(TAG, "getResourcesMap", "enter method: " + this.nameMappingPinsName)
        if (this.nameMappingPinsName != null) {
            return this.nameMappingPinsName
        }

        NodeList resourcesNodeList = getResourcesNodeList()
        nameMappingPinsName = new HashMap<String, String>()
        if (resourcesNodeList == null || resourcesNodeList.length == 0)
            return nameMappingPinsName

        def resourcesNodeLength = resourcesNodeList.getLength()
        for (int i = 0; i < resourcesNodeLength; i++) {
            Element resourcesElement = (Element) resourcesNodeList.item(i)
            String path = resourcesElement.getAttribute("path")
            String moduleName = path.replace(projectPath, "")
            if (moduleName.startsWith(File.separator + "build")) {
                continue
            }

            moduleName = moduleName.substring(0, moduleName.indexOf(SRC))
            if (File.separator == "\\") {
                moduleName = moduleName.replaceAll("\\\\", ":")
            } else {
                moduleName = moduleName.replaceAll("/", ":")
            }
            NodeList fileNodeList = resourcesElement.getElementsByTagName("file")
            def fileNodeLength = fileNodeList.getLength()
            if (fileNodeLength <= 0) {
                continue
            }
            for (int j = 0; j < fileNodeLength; j++) {
                Element fileElement = (Element) fileNodeList.item(j)
                String name = fileElement.getAttribute("name")
                if (name == "") {
                    NodeList nodeList = fileElement.getChildNodes()
                    def nodeLength = nodeList.getLength()
                    if (nodeLength <= 0) {
                        continue
                    }
                    for (int k = 0; k < nodeLength; k++) {
                        Element childElement = (Element) nodeList.item(k)
                        name = childElement.getAttribute("name")
                        nameMappingPinsName.put(name, moduleName)
                    }
                } else {
                    nameMappingPinsName.put(name, moduleName)
                }
            }
        }

        Logger.info(TAG, "getResourcesMap", "exit method: " + nameMappingPinsName)
        return nameMappingPinsName
    }


    @Override
    String toString() {
        return "MergedReport{" +
                "projectPath='" + projectPath + '\'' +
                ", resourcesMergerFile=" + resourcesMergerFile +
                ", resourcesNodeList=" + resourcesNodeList +
                ", nameMappingPinsName=" + nameMappingPinsName +
                '}';
    }
}