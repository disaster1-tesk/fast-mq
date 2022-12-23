package io.github.fastmq.infrastructure.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.util.*;

/**
 * Bean和Map类型转换工具类,参考自Barry
 *
 * @author disaster
 * @version 1.0
 */
@Slf4j
public final class BeanMapUtils {

    private static String FastMQListenerInfo = "io.github.fastmq.domain.consumer.instantaneous.FastMQListener";

    private static String FastMQDelayListenerInfo = "io.github.fastmq.domain.consumer.delay.FastMQDelayListener";

    /**
     * To bean object.
     *
     * @param type the type
     * @param map  the map
     * @return the object
     */
    @SneakyThrows
    public static final Object toBean(Class<?> type, Map<Object, ? extends Object> map) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        Type generic = null;
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface.getTypeName().startsWith(FastMQListenerInfo)) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                generic = parameterizedType.getActualTypeArguments()[0];
            }
        }
        if (Objects.isNull(generic)) return map;
        log.info(generic.getTypeName());
        Class<?> aClass = Class.forName(generic.getTypeName());
        //hutool支持父类的解析
        return BeanUtil.mapToBean(map, aClass, true, CopyOptions.create());
    }

    @SneakyThrows
    public static final Object toBean(Class<?> type, String msg) {
        Type[] genericInterfaces = type.getGenericInterfaces();
        Type generic = null;
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface.getTypeName().startsWith(FastMQDelayListenerInfo)) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                generic = parameterizedType.getActualTypeArguments()[0];
            }
        }
        if (Objects.isNull(generic)) return msg;
        log.info(generic.getTypeName());
        Class<?> aClass = Class.forName(generic.getTypeName());
        return JSON.parseObject(msg, aClass);
    }


    /**
     * Convert map 2 bean list.
     *
     * @param <T>  the type parameter
     * @param list the list
     * @param cls  the cls
     * @return the list
     */
    public static <T> List<T> convertMap2Bean(List<Map<String, Object>> list, Class<T> cls) {
        List<T> returnList = null;
        if (list != null && list.size() > 0 && cls != null) {
            returnList = new ArrayList<>();
            for (Map<String, Object> map : list) {
                returnList.add(map2Object(map, cls));
            }
        } else {
            log.info("传入的对象数据不能为空数组数据，请核查");
        }
        return returnList;
    }


    /**
     * Map 2 object t.
     *
     * @param <T>      the type parameter
     * @param paramMap the param map
     * @param cls      the cls
     * @return the t
     */
    public static <T> T map2Object(Map<String, Object> paramMap, Class<T> cls) {
        return JSONObject.parseObject(JSONObject.toJSONString(paramMap), cls);
    }


    /**
     * To map map.
     *
     * @param bean the bean
     * @return the map
     * @throws IntrospectionException    the introspection exception
     * @throws IllegalAccessException    the illegal access exception
     * @throws InvocationTargetException the invocation target exception
     */
    public static final Map<String, Object> toMap(Object bean)
            throws IntrospectionException, IllegalAccessException, InvocationTargetException {
        Map<String, Object> returnMap = new HashMap<String, Object>();
        BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
        PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        for (int i = 0; i < propertyDescriptors.length; i++) {
            PropertyDescriptor descriptor = propertyDescriptors[i];
            String propertyName = descriptor.getName();
            if (!propertyName.equals("class")) {
                Method readMethod = descriptor.getReadMethod();
                Object result = readMethod.invoke(bean, new Object[0]);
                if (result != null) {
                    returnMap.put(propertyName, result);
                } else {
                    returnMap.put(propertyName, "");
                }
            }
        }
        return returnMap;
    }
}
