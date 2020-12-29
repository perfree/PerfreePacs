package com.perfree.dicom.common;

import java.io.File;

/**
 * DicomFileEvent
 * @author Perfree
 */
public class DicomFileEvent {
    private final File file;

    public DicomFileEvent(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }
}
