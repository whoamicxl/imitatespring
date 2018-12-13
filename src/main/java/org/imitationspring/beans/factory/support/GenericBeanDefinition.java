package org.imitationspring.beans.factory.support;

import org.imitationspring.beans.factory.config.BeanDefinition;

/**
 * bean在spring容器中的数据结构
 * @author liaocx
 */
public class GenericBeanDefinition implements BeanDefinition {

    private String id;
    private String beanClassName;
    private String scope = BeanDefinition.SCOPE_DEFAULT;

    public GenericBeanDefinition(String id, String beanClassName) {
        this.id = id;
        this.beanClassName = beanClassName;
    }

    @Override
    public String getBeanClassName() {
        return this.beanClassName;
    }

    @Override
    public String getScope() {
        return this.scope;
    }

    @Override
    public void setScope(String scope) {
        this.scope = scope;
    }

    @Override
    public boolean isSingleton() {
        return SCOPE_SINGLETON.equals(this.scope) || SCOPE_DEFAULT.equals(this.scope);
    }

    @Override
    public boolean isPrototype() {
        return SCOPE_PROTOTYPE.equals(this.scope);
    }
}
