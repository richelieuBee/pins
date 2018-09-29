from [https://github.com/EastWoodYang/MicroModule](MicroModule]), simplify some dependencies and code

change log:
1. update version, format log info;
2. add logger class;
3. format log info, add cached map for pins-project to avoids duplicate build;
4. now sill project is the one be pined  first by default, you can still one to replace;
5. add example to show R.java's invoke which likes Android default way;
6. improve package structure, degrade version to 0.9.2;
7. catch FileNotFoundException when No pins-project has res folder;
8. improve package structure. clear up R.java generate cod and manifest merge code;
9.  change plugins apply name, change logger from normal class to static class;
10. release v1.0.0
    Modified: clear up code check code;
    Modified: change pins() to pinProject() on dependencies configuration;
    Modified: add log level and debug model;
    Modified: improve example code.
11. Fix: still generate R.java when native R.java not exists;
    Fix: pinProject() spell wrong - pinProjct();
    Fix: skip code-check-manifest.xml generate in NO res module when first time;
    Modified: log info format and WrongDependenciesException info format