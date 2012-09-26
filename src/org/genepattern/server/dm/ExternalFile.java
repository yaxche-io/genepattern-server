package org.genepattern.server.dm;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.genepattern.server.config.ServerConfiguration.Context;

/**
 * Implementation for handling external URLs to files in the way we handle other files
 * @author tabor
 */
public class ExternalFile extends GpFilePath {
    private static Logger log = Logger.getLogger(ExternalFile.class);
    
    private URL url = null;
    private URI uri = null;
    
    public ExternalFile(String url) {
        setUrl(url);
    }
    
    public ExternalFile(URL url) {
        setUrl(url);
    }
    
    public void setUrl(URL url) {
        this.url = url;
        
        String filename = url.getFile().substring(url.getFile().lastIndexOf("/") + 1);
        this.setName(filename);
        
        String extension = filename.substring(filename.lastIndexOf(".") + 1);
        this.setKind(extension);
        this.setExtension(extension);
    }
    
    public void setUrl(String url) {
        try {
            setUrl(new URL(url));
        }
        catch (MalformedURLException e) {
            log.error("Unable to convert to URL: " + url);
        }
    }
    
    @Override
    public URL getUrl() throws Exception {
        return url;
    }

    @Override
    public URI getRelativeUri() {
        if (uri == null && url != null) {
            try {
                uri = new URI(url.toString());
            }
            catch (URISyntaxException e) {
                log.error("Unable to convert the URL to a URI: " + url);
            }
        }
        return uri;
    }

    @Override
    public File getServerFile() {
        log.error("Not implemented exception: ExternalFile.getServerFile()");
        return null;
    }

    @Override
    public File getRelativeFile() {
        log.error("Not implemented exception: ExternalFile.getRelativeFile()");
        return null;
    }

    @Override
    public boolean canRead(boolean isAdmin, Context userContext) {
        log.error("Not implemented exception: ExternalFile.canRead()");
        return false;
    }

    @Override
    public String getFormFieldValue() {
        return getName();
    }

    @Override
    public String getParamInfoValue() {
        log.error("Not implemented exception: ExternalFile.getParamInfoValue()");
        return null;
    }

}