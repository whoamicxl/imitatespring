package org.imitationspring.beans;

/**
 * @author liaocx
 */
public class BeansException extends RuntimeException {
    public BeansException(String msg) {
        super(msg);	}

    public BeansException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
