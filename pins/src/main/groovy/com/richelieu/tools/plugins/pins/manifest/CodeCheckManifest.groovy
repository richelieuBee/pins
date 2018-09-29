package com.richelieu.tools.plugins.pins.manifest

import com.richelieu.tools.plugins.pins.logger.Logger
import com.richelieu.tools.plugins.pins.resource.RecordItem
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * build/pins/code-check-manifest.xml instance
 *
 * @author richelieu 09.28 2018
 */
class CodeCheckManifest {

    static final String TAG = "CodeCheckManifest"

    Document document
    Element rootElement

    String packageName
    Map<String, RecordItem> pathMappingResourcesRecord
    Map<String, RecordItem> pathMappingClassesRecord

    /**
     * load file build/pins/code-check-manifest.xml
     *
     * @param sourceFile file instance
     */
    void load(File sourceFile) {
        if (!sourceFile.exists()) {
            return
        }

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        document = builderFactory.newDocumentBuilder().parse(sourceFile)
        rootElement = document.documentElement
        packageName = rootElement == null ? "" : rootElement.getAttribute("package")
    }

    /**
     * get package name from code-check-manifest.xml
     */
    String getPackageName() {
        return packageName
    }

    /**
     * get all modified resources
     */
    long getResourcesLastModified() {
        if (rootElement == null) {
            return 0
        }

        Element resourcesElement = (Element) rootElement.getElementsByTagName("resources").item(0)
        return resourcesElement.getAttribute("last-modified").toLong()
    }

    /**
     * set resource modified timestamp
     *
     * @param lastModified timestamp
     */
    void setResourcesLastModified(long lastModified) {
        resourcesLastModified = lastModified
    }

    /**
     * get all resource content from code-check-manifest.xml
     */
    Map<String, RecordItem> getModifiedResourceRecord() {
        Logger.info(TAG, "getModifiedResourceRecord", "enter method: " + pathMappingResourcesRecord)
        if (pathMappingResourcesRecord != null) {
            return pathMappingResourcesRecord
        }

        pathMappingResourcesRecord = new HashMap<>()
        if (rootElement == null) {
            return pathMappingResourcesRecord
        }

        NodeList resourcesNodeList = rootElement.getElementsByTagName("resources")
        if (resourcesNodeList.length == 0) {
            return pathMappingResourcesRecord
        }

        //resolve all resource content
        Element resourcesElement = (Element) resourcesNodeList.item(0)
        NodeList fileNodeList = resourcesElement.getElementsByTagName("file")
        for (int i = 0; i < fileNodeList.getLength(); i++) {
            Element fileElement = (Element) fileNodeList.item(i)
            RecordItem resourceFile = new RecordItem()
            resourceFile.name = fileElement.getAttribute("name")
            resourceFile.pinsName = fileElement.getAttribute("pinsName")
            resourceFile.path = fileElement.getAttribute("path")
            resourceFile.lastModified = fileElement.getAttribute("lastModified").toLong()
            pathMappingResourcesRecord.put(resourceFile.path, resourceFile)
        }

        Logger.info(TAG, "getModifiedResourceRecord", "exit method: " + pathMappingResourcesRecord)
        return pathMappingResourcesRecord
    }

    /**
     * get all class content from code-check-manifest.xml
     */
    Map<String, RecordItem> getModifiedClassRecord() {
        Logger.info(TAG, "getModifiedClassRecord", "enter method: " + pathMappingClassesRecord)
        if (pathMappingClassesRecord != null) {
            return pathMappingClassesRecord
        }

        pathMappingClassesRecord = new HashMap<>()
        if (rootElement == null) {
            return pathMappingClassesRecord
        }

        NodeList classesNodeList = rootElement.getElementsByTagName("classes")
        if (classesNodeList.length == 0) {
            return pathMappingClassesRecord
        }

        //resolve all class content
        Element classesElement = (Element) classesNodeList.item(0)
        NodeList fileNodeList = classesElement.getElementsByTagName("file")
        for (int i = 0; i < fileNodeList.getLength(); i++) {
            Element fileElement = (Element) fileNodeList.item(i)
            RecordItem recordItem = new RecordItem()
            recordItem.name = fileElement.getAttribute("name")
            recordItem.pinsName = fileElement.getAttribute("pinsName")
            recordItem.path = fileElement.getAttribute("path")
            recordItem.lastModified = fileElement.getAttribute("lastModified").toLong()
            pathMappingClassesRecord.put(recordItem.path, recordItem)
        }
        Logger.info(TAG, "getModifiedClassRecord", "exit method: " + pathMappingClassesRecord)
        return pathMappingClassesRecord
    }

    /**
     * update current cached data to code-check-manifest.xml
     */
    void update(File destFile) {
        Logger.info(TAG, "update", "with: destFile = " + destFile + "")

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document documentTemp = builderFactory.newDocumentBuilder().newDocument()
        Element pinsModuleXmlTemp = documentTemp.createElement("pins-module")
        pinsModuleXmlTemp.setAttribute("package", packageName)

        //append resources record
        Element resourcesElement = documentTemp.createElement("resources")
        pinsModuleXmlTemp.appendChild(resourcesElement)
        if (pathMappingResourcesRecord != null) {
            pathMappingResourcesRecord.each {
                RecordItem recordItem = it.value
                Element fileElement = documentTemp.createElement("file")
                fileElement.setAttribute("name", recordItem.name)
                fileElement.setAttribute("path", recordItem.path)
                fileElement.setAttribute("lastModified", recordItem.lastModified.toString())
                fileElement.setAttribute("pinsName", recordItem.pinsName)
                resourcesElement.appendChild(fileElement)
            }
        }

        //append classes record
        if (pathMappingClassesRecord != null) {
            Element classesElement = documentTemp.createElement("classes")
            pinsModuleXmlTemp.appendChild(classesElement)
            pathMappingClassesRecord.each {
                RecordItem recordItem = it.value
                Element fileElement = documentTemp.createElement("file")
                fileElement.setAttribute("name", recordItem.name)
                fileElement.setAttribute("path", recordItem.path)
                fileElement.setAttribute("lastModified", recordItem.lastModified.toString())
                fileElement.setAttribute("pinsName", recordItem.pinsName)
                classesElement.appendChild(fileElement)
            }
            pinsModuleXmlTemp.appendChild(classesElement)

        } else if (rootElement != null) {
            NodeList classesNodeList = rootElement.getElementsByTagName("classes")
            if (classesNodeList.length == 1) {
                Element classesElement = (Element) classesNodeList.item(0)
                pinsModuleXmlTemp.appendChild(documentTemp.importNode(classesElement, true))
            }
        }

        //write cached data to file
        Transformer transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        transformer.transform(new DOMSource(pinsModuleXmlTemp), new StreamResult(destFile))
    }
}