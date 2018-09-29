package com.richelieu.tools.plugins.pins.resource

class RecordItem {
    String path
    String name
    Long lastModified
    String pinsName


    @Override
    String toString() {
        return "RecordItem{" +
                "path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", lastModified=" + lastModified +
                ", pinsName='" + pinsName + '\'' +
                '}';
    }
}