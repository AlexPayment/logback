/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.rolling;

import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.helper.CompressionMode;
import ch.qos.logback.core.rolling.helper.Compressor;
import ch.qos.logback.core.rolling.helper.FileNamePattern;
import ch.qos.logback.core.rolling.helper.RenameUtil;
import ch.qos.logback.core.spi.ContextAwareBase;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implements methods common to most, it not all, rolling policies. Currently
 * such methods are limited to a compression mode getter/setter.
 * 
 * @author Ceki G&uuml;lc&uuml;
 */
public abstract class RollingPolicyBase extends ContextAwareBase implements RollingPolicy {
    protected CompressionMode compressionMode = CompressionMode.NONE;

    FileNamePattern fileNamePattern;
    // fileNamePatternStr is always slashified, see setter
    protected String fileNamePatternStr;

    protected RenameUtil renameUtil = new RenameUtil();

    private FileAppender<?> parent;

    // use to name files within zip file, i.e. the zipEntry
    FileNamePattern zipEntryFileNamePattern;
    private boolean started;

    /**
     * Given the FileNamePattern string, this method determines the compression
     * mode depending on last letters of the fileNamePatternStr. Patterns ending
     * with .gz imply GZIP compression, endings with '.zip' imply ZIP compression.
     * Otherwise and by default, there is no compression.
     * 
     */
    protected void determineCompressionMode() {
        if (fileNamePatternStr.endsWith(".gz")) {
            addInfo("Will use gz compression");
            compressionMode = CompressionMode.GZ;
        } else if (fileNamePatternStr.endsWith(".zip")) {
            addInfo("Will use zip compression");
            compressionMode = CompressionMode.ZIP;
        } else {
            addInfo("No compression will be used");
            compressionMode = CompressionMode.NONE;
        }
    }

    public void setFileNamePattern(String fnp) {
        fileNamePatternStr = fnp;
    }

    public String getFileNamePattern() {
        return fileNamePatternStr;
    }

    public CompressionMode getCompressionMode() {
        return compressionMode;
    }

    public boolean isStarted() {
        return started;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
    }

    public void setParent(FileAppender<?> appender) {
        this.parent = appender;
    }

    public boolean isParentPrudent() {
        return parent.isPrudent();
    }

    public String getParentsRawFileProperty() {
        return parent.rawFileProperty();
    }

    protected abstract Compressor getCompressor();

    protected Future<?> renameRawAndAsyncCompress(String nameOfCompressedFile, String innerEntryName) throws RolloverFailure {
        String parentsRawFile = getParentsRawFileProperty();
        String tmpTarget = nameOfCompressedFile + System.nanoTime() + ".tmp";
        renameUtil.rename(parentsRawFile, tmpTarget);
        return getCompressor().asyncCompress(tmpTarget, nameOfCompressedFile, innerEntryName);
    }

    protected void waitForAsynchronousJobToStop(Future<?> aFuture, String jobDescription) {
        if (aFuture != null) {
            try {
                aFuture.get(CoreConstants.SECONDS_TO_WAIT_FOR_COMPRESSION_JOBS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                addError("Timeout while waiting for " + jobDescription + " job to finish", e);
            } catch (Exception e) {
                addError("Unexpected exception while waiting for " + jobDescription + " job to finish", e);
            }
        }
    }
}
