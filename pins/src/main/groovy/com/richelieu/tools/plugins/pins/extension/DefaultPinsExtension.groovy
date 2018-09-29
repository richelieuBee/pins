package com.richelieu.tools.plugins.pins.extension

import com.richelieu.tools.plugins.pins.PinsProject
import com.richelieu.tools.plugins.pins.logger.Logger
import org.gradle.api.GradleException
import org.gradle.api.Project

class DefaultPinsExtension implements PinsExtension {

    static final String TAG = "DefaultPinsExtension"

    Project project
    Map<String, PinsProject> pathMappingPins
    OnProjectAddedListener onProjectAddedListener

    /**
     * sill is pins-project type which contains base config and AndroidManifest.xml
     */
    PinsProject sillProject

    /**
     * pins-project list of current module has pined.
     */
    List<PinsProject> lstOfPinedProject

    DefaultPinsExtension(Project project) {
        this.project = project
        this.lstOfPinedProject = new ArrayList<>()
        this.pathMappingPins = new HashMap<>()
    }


    @Override
    void pin(String... pinsProjects) {
        Logger.println()
        Logger.info(TAG, "pin", "with: pinsProjects = " + pinsProjects + "")

        int count = pinsProjects.size()
        for (int index = 0; index < count; index++) {
            PinsProject pinsProject = buildPinProject(pinsProjects[index])
            if (pinsProject == null) {
                throw new GradleException("can't find specified pins-project '${pinsProjects[index]}'.")
            }

            if (sillProject == null) {
                Logger.info(TAG, "pin", "sill project is null, default it.")
                sill(pinsProjects[index])
            } else {
                addPinsProject(pinsProject)
                if (onProjectAddedListener != null) {
                    onProjectAddedListener.onProjectAdd(pinsProject, false)
                }
            }

        }
    }

    @Override
    void sill(String sillProject) {
        Logger.info(TAG, "sill", "with: sillProject = " + sillProject + "")

        this.sillProject = buildPinProject(sillProject)
        if (this.sillProject == null) {
            throw new GradleException("can't find specified sill-project '${sillProject}'.")
        }
        if (onProjectAddedListener != null) {
            onProjectAddedListener.onProjectAdd(this.sillProject, true)
        }
    }

    @Override
    void debug(boolean isDebug) {
        Logger.error(TAG, "debug", "with: isDebug = " + isDebug + "")
        Logger.setLogLevel(isDebug ? Logger.VERBOSE : Logger.ERROR)
    }

    /**
     * build pins-project instance by path
     *
     * @param path pins-project path
     * @return pins-project
     */
    PinsProject buildPinProject(String path) {
        Logger.info(TAG, "buildPinProject", "with: path = " + path + "")


        if (pathMappingPins.containsKey(path)) {
            PinsProject cachedProject = pathMappingPins.get(path)
            Logger.info(TAG, "buildPinProject", "cached: " + cachedProject)
            return cachedProject
        } else {
            //get all dependencies path
            String[] paths = removeTrailingColon(path).split(":")
            int pathCount = paths.size()

            File parentDir = project.projectDir
            for (int index = 0; index < pathCount; index++) {
                parentDir = new File(parentDir, paths[index])
            }
            File pinsProjectDir = parentDir.canonicalFile
            String pinsProjectName = pinsProjectDir.absolutePath.replace(project.projectDir.absolutePath, "")
            if (File.separator == "\\") {
                pinsProjectName = pinsProjectName.replaceAll("\\\\", ":")
            } else {
                pinsProjectName = pinsProjectName.replaceAll("/", ":")
            }
            if (!pinsProjectDir.exists()) {
                return null
            }

            PinsProject pinsProject = new PinsProject()
            pinsProject.name = pinsProjectName
            pinsProject.pinProjectDir = pinsProjectDir
            Logger.info(TAG, "buildPinProject", "build new: " + pinsProject.toString())

            pathMappingPins.put(path, pinsProject)
            return pinsProject
        }
    }

    /**
     * add pins-project to current real project.
     *
     * @param pinsProject pins-project
     */
    private void addPinsProject(PinsProject pinsProject) {
        Logger.info(TAG, "addPinsProject", "with: pinsProject = " + pinsProject + "")

        for (int index = 0; index < lstOfPinedProject.size(); index++) {
            if (lstOfPinedProject.get(index).name == pinsProject.name) {
                return
            }
        }
        lstOfPinedProject.add(pinsProject)
    }

    private static String removeTrailingColon(String pinsProjectPath) {
        return pinsProjectPath.startsWith(":") ? pinsProjectPath.substring(1) : pinsProjectPath
    }

    /**
     * listen pin project added event.
     */
    interface OnProjectAddedListener {
        void onProjectAdd(PinsProject newProject, boolean isSill)
    }
}
