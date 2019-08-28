package com.mk.servlet;


import com.mk.annotation.*;
import org.apache.commons.lang.StringUtils;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

public class XDispatchServlet extends HttpServlet {

    // 跟web.xml中param-name的值一致
    private static final String LOCATION = "contextConfigLocation";

    // 保存所有配置信息
    Properties p = new Properties();

    // 保存所有扫描到的雷鸣
    private List<String> classNames = new ArrayList<String>();

    // 核心IOC容器，保存所有初始化的bean
    private Map<String, Object> ioc = new HashMap<String, Object>();

    // 存储访问路径和请求方法对应关系
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();


    /**
     * 数据初始化
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        // 2、扫描包下的类文件
        doScanner(p.getProperty("scanPackage"));
        // 3、初始化类实例，保存到ioc容器中
        doInstance();
        // 4、注入对象
        doAutowired();
        // 5、初始化请求路径和方法对应关系
        initHandlerMapping();
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispather(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail:\n" + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }


    /**
     * 加载配置文件
     * @param location
     */
    private void doLoadConfig(String location) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(location);
        try {
            p.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * 扫描包下的所有类
     * @param packageName
     */
    public void doScanner(String packageName) {
        // 将所有的包路径转换成文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            // 如果是文件目录，继续递归
            if(file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                classNames.add(packageName + "." + file.getName().replace(".class", ""));
            }
        }
    }


    /**
     * 将首字母转换成小写
     * @param str
     * @return
     */
    private String lowerFirstCase(String str) {
        char[] ch = str.toCharArray();
        ch[0] += 32;
        return String.valueOf(ch);
    }


    /**
     * 初始化类实例，保存到ioc容器中
     */
    public void doInstance() {
        // 如果包下没有类，直接返回
        if(classNames.isEmpty()) {
            return;
        }

        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                // 类上面添加的XController注解
                if(clazz.isAnnotationPresent(XController.class)) {
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if(clazz.isAnnotationPresent(XService.class)) {
                    XService xService = clazz.getAnnotation(XService.class);
                    // 如果注解包含自定义名称
                    if(StringUtils.isNotEmpty(xService.value())) {
                        ioc.put(xService.value(), clazz.newInstance());
                        continue;
                    }

                    // 获取实现类的接口
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 依赖注入
     */
    public void doAutowired() {
        // 如果ioc容器为空，直接返回
        if(ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                // 属性变量未标注XAutowired注解
                if (!field.isAnnotationPresent(XAutowired.class)) {
                    continue;
                }

                XAutowired xAutowired = field.getAnnotation(XAutowired.class);
                String beanName = xAutowired.value().trim();
                if(StringUtils.isEmpty(beanName)) {
                    beanName = field.getType().getName();
                }

                // 设置私有成员变量访问权限
                field.setAccessible(true);

                try {
                    // 给成员变量赋值
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * 处理url
     * @param url
     * @return
     */
    public String handleUrl(String url) {
        if(!url.startsWith("/")) {
            url = "/" + url;
        }
        return url;
    }



    /**
     * 初始化请求路径和方法的对应关系
     */
    public void initHandlerMapping() {
        // 如果ioc容器为空，则直接返回
        if(ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            // 如果不是控制器类则跳过
            if(!clazz.isAnnotationPresent(XController.class)) {
                continue;
            }

            String baseUrl = StringUtils.EMPTY;
            if(clazz.isAnnotationPresent(XRequestMapping.class)) {
                XRequestMapping mapping = clazz.getAnnotation(XRequestMapping.class);
                baseUrl = handleUrl(mapping.value());
            }

            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if(!method.isAnnotationPresent(XRequestMapping.class)) {
                    continue;
                }

                XRequestMapping requestMapping = method.getAnnotation(XRequestMapping.class);
                String url = baseUrl + handleUrl(requestMapping.value().replaceAll("/+", "/"));
                handlerMapping.put(url, method);
            }
        }
    }


    /**
     * 根据请求路径执行对应方法
     * @param req
     * @param resp
     */
    public void doDispather(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(handlerMapping.isEmpty()) {
            return;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replace("/+", "/");

        if(!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!");
            return;
        }

        Map<String, String[]> params = req.getParameterMap();
        List<Object> args = new ArrayList<>(Arrays.asList(req, resp));
        Method method = handlerMapping.get(url);
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            if(!parameter.isAnnotationPresent(XRequestParam.class)) {
                continue;
            }
            XRequestParam xRequestParam = parameter.getAnnotation(XRequestParam.class);
            String type = parameter.getParameterizedType().getTypeName();
            switch (type){
                case "java.lang.Integer":
                    args.add(Integer.valueOf(params.get(xRequestParam.value())[0]));
                    break;
                case "java.lang.Long":
                    args.add(Long.valueOf(params.get(xRequestParam.value())[0]));
                    break;
                default:
                    args.add(params.get(xRequestParam.value())[0]);
                    break;
            }
        }

        String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
        // 执行方法

        method.invoke(ioc.get(beanName), args.toArray());
    }
}
