package org.imitatespring.context.support;

import org.imitatespring.core.io.FileSystemResource;
import org.imitatespring.core.io.Resource;

/**
 * @author liaocx
 */
public class FileSystemXmlApplicationContext extends AbstractApplicationContext {

    public FileSystemXmlApplicationContext(String configFile) {
        super(configFile);
    }

    @Override
    protected Resource getResourceByPath(String path) {
        return new FileSystemResource(path);
    }
}
