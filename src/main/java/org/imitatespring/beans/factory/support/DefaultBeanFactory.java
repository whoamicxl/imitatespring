package org.imitatespring.beans.factory.support;

import org.apache.commons.beanutils.BeanUtils;
import org.imitatespring.beans.PropertyValue;
import org.imitatespring.beans.SimpleTypeConverter;
import org.imitatespring.beans.factory.config.*;
import org.imitatespring.beans.factory.BeanCreationException;
import org.imitatespring.util.ClassUtils;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 底层的bean工厂, 用于注册bean的Definition, 获取bean实例以及Definition
 * @author liaocx
 */
public class  DefaultBeanFactory extends DefaultSingletonBeanRegistry
        implements ConfigurableBeanFactory, BeanDefinitionRegistry {

    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();

    private ClassLoader beanClassLoader;

    private List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    /**
     * BeanDefinitionRegistry
     */
    @Override
    public BeanDefinition getBeanDefinition(String beanId) {
        return beanDefinitionMap.get(beanId);
    }

    @Override
    public void registerBeanDefinition(String beanId, BeanDefinition bd) {
        beanDefinitionMap.put(beanId, bd);
    }

    /**
     * BeanFactory
     */
    @Override
    public Object getBean(String beanId) {
        BeanDefinition bd = getBeanDefinition(beanId);
        if (Objects.isNull(bd)) {
            throw new BeanCreationException("BeanDefinition is not exist");
        }
        if (bd.isSingleton()) {
            //bean的scope是singleton
            Object singletonInstance = super.getSingleton(beanId);
            if (singletonInstance == null) {
                singletonInstance = this.createBean(bd);
                super.registerSingleton(beanId, singletonInstance);
            }
            return singletonInstance;
        }
        return createBean(bd);
    }

    private Object createBean(BeanDefinition bd) {
        // 创建实例
        Object bean = instantiateBean(bd);
        // 设置属性,  如setter注入
        populateBean(bd, bean);
        //使用commons-beanutils封装的方法直接setproperty中声明的值
        //populateBeanUseCommonBeanUtils(bd, bean);
        return bean;
    }

    private Object instantiateBean(BeanDefinition bd) {
        if (bd.hasConstructorArgumentValues()) {
            //存在有参构造器
            ConstructorResolver resolver = new ConstructorResolver(this);
            return resolver.autowireConstructor(bd);
        } else {
            //使用默认的无参构造函数
            String beanClassName = bd.getBeanClassName();
            try {
                //加载一次beanClass对象, 将其存入缓存
                bd.resolveBeanClass(getBeanClassLoader());
                Class<?> clz = bd.getBeanClass();
                //默认有个无参的构造函数
                return clz.newInstance();
            } catch (Exception e) {
                throw new BeanCreationException("create bean for " + beanClassName + " failed", e);
            }
        }
    }

    private void populateBean(BeanDefinition bd, Object bean) {

        for (BeanPostProcessor processor : this.getBeanPostProcessors()) {
            if (processor instanceof InstantiationAwareBeanPostProcessor) {
                ((InstantiationAwareBeanPostProcessor) processor).postProcessPropertyValues(bean, bd.getId());
            }
        }

        //取出这个bean中所有的propertyValue, 也有可能没有
        List<PropertyValue> pvs = bd.getPropertyValue();
        if (pvs == null || pvs.isEmpty()) {
            return;
        }
        BeanDefinitionValueResolver resolver = new BeanDefinitionValueResolver(this);
        SimpleTypeConverter converter = new SimpleTypeConverter();
        try {
            for (PropertyValue pv : pvs) {
                String propertyName = pv.getName();
                Object assembleValue = pv.getValue();
                //value or ref对应的bean
                Object resolvedValue = resolver.resolveValueIfNecessary(assembleValue);
                BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
                //属性的描述器, 每次循环都会获取bean中声明的所有属性, 将其和propertyName进行匹配, 命中就通过反射set值
                PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
                for (PropertyDescriptor pd : pds) {
                    if (pd.getName().equals(propertyName)) {
                        Object convertedValue = converter.convertIfNecessary(resolvedValue, pd.getPropertyType());
                        pd.getWriteMethod().invoke(bean, convertedValue);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            throw new BeanCreationException("Failed to obtain BeanInfo for class [" + bd.getBeanClassName() + "]", ex);
        }
    }

    private void populateBeanUseCommonBeanUtils(BeanDefinition bd, Object bean) {
        //取出这个bean中所有的propertyValue, 也有可能没有
        List<PropertyValue> pvs = bd.getPropertyValue();
        if (pvs == null || pvs.isEmpty()) {
            return;
        }
        BeanDefinitionValueResolver resolver = new BeanDefinitionValueResolver(this);
        try {
            for (PropertyValue pv : pvs) {
                String propertyName = pv.getName();
                Object assembleValue = pv.getValue();
                //value or ref对应的bean
                Object resolvedValue = resolver.resolveValueIfNecessary(assembleValue);
                BeanUtils.setProperty(bean, propertyName, resolvedValue);
            }
        } catch (Exception ex) {
            throw new BeanCreationException("Failed to obtain BeanInfo for class [" + bd.getBeanClassName() + "]", ex);
        }
    }


    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Override
    public ClassLoader getBeanClassLoader() {
        return beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader();
    }

    /**
     * 将依赖注入传入的参数与已经缓存的beanDefinition对象进行比较, 如果存在则获取其实例bean
     * @param descriptor
     * @return
     */
    @Override
    public Object resolveDependency(DependencyDescriptor descriptor) {
        Class<?> typeToMatch = descriptor.getDependencyType();
        for (BeanDefinition bd : this.beanDefinitionMap.values()) {
            //确保BeanDefinition对象中 有beanClass对象
            resolveBeanClass(bd);
            Class<?> beanClass = bd.getBeanClass();
            //默认要依赖注入的bean已经提前缓存到BeanFactory中
            if (typeToMatch.isAssignableFrom(beanClass)) {
                return this.getBean(bd.getId());
            }
        }
        return null;
    }

    private void resolveBeanClass(BeanDefinition bd) {
        if (bd.hasBeanClass()) {
            return;
        } else {
            try {
                bd.resolveBeanClass(this.getBeanClassLoader());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("can't load class: " + bd.getBeanClassName());
            }
        }
    }


    @Override
    public void addBeanPostProcessor(BeanPostProcessor postProcessor) {
        this.beanPostProcessors.add(postProcessor);
    }

    @Override
    public List<BeanPostProcessor> getBeanPostProcessors() {
        return this.beanPostProcessors;
    }
}