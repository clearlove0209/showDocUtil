package com.javan.showdocutil.util;

import com.javan.showdocutil.model.*;
import com.sun.javadoc.*;
import com.sun.tools.javadoc.MethodDocImpl;

import java.util.*;
import java.util.stream.Collectors;

class JavaDocReader {
    // 文件夹名
    private static final Map<String, String> TYPE_FOLDER_MAP = new HashMap<>();
    // 文件名
    private static final Map<String, String> METHOD_PAGE_TITLE_MAP = new HashMap<>();
    // 请求参数内容
    private static final Map<String, String> METHOD_REQ_PARAM_MAP = new HashMap<>();
    // 返回参数内容
    private static final Map<String, String> METHOD_RETURN_MAP = new HashMap<>();

    // 返回参数内容
    private static final Set<String> CLASS_CYCLE_SET = new HashSet<>();


    private static RootDoc root;

    public static String getFolder(String typeName) {
        return TYPE_FOLDER_MAP.getOrDefault(typeName, typeName);
    }

    public static String getTitle(String typeName) {
        return METHOD_PAGE_TITLE_MAP.getOrDefault(typeName, typeName);
    }

    public static String getRequestParam(String typeName) {
        return METHOD_REQ_PARAM_MAP.get(typeName);
    }

    public static String getReturn(String methodName) {
        return METHOD_RETURN_MAP.get(methodName);
    }

    // 一个简单Doclet,收到 RootDoc对象保存起来供后续使用
    // 参见参考资料6
    public static class Doclet {

        public Doclet() {
        }

        public static boolean start(RootDoc root) {
            JavaDocReader.root = root;
            return true;
        }

        public static LanguageVersion languageVersion() {
            return LanguageVersion.JAVA_1_5;
        }
    }


    // 显示DocRoot中的基本信息
    private static void doParse() throws Exception {
        // 只有一个
        ClassDoc[] classes = root.classes();
        // 后续不需要root 可以覆盖
        for (ClassDoc aClass : classes) {
            // 添加文件夹
            addFolder(aClass);
            //
            MethodDoc[] methods = aClass.methods();
            for (MethodDoc method : methods) {
                // current method
                ControllerClassInfo controllerClassInfo = ControllerClazzThreadLocal.get();
                MethodInfo methodByMethodName = controllerClassInfo.getMethodByMethodName(method.name());
                if (methodByMethodName == null) {
                    continue;
                }
                MethodInfoThreadLocal.set(methodByMethodName);

                // 添加页面标题
                addPageTitle(method);
                // 请求参数
                addRequestParam(method);
                // 返回值
                addRequestReturn(method);
                MethodInfoThreadLocal.remove();
            }
        }
    }

    private static void addRequestReturn(MethodDoc method) throws Exception {

        MethodDocImpl doc = (MethodDocImpl) method;
        // 方法名
        String methodName = method.qualifiedName();
        Type type = method.returnType();
        String typeName = type.qualifiedTypeName();

        StringBuilder builder = new StringBuilder();
        String comment = "结果";
        Tag[] returns = method.tags("return");
        if (returns != null || returns.length != 0) {
            comment = Arrays.stream(returns).map(Tag::text).collect(Collectors.joining(","));
        }
        // base type
        builder.append("|");
        builder.append("result");
        builder.append("|");
        builder.append("");
        builder.append("|");
        builder.append(BaseTypeUtil.getActualTypeName(type));
        builder.append("|");
        builder.append("结果");
        builder.append("|");
        builder.append("\r\n");
        if (!BaseTypeUtil.isBaseType(typeName)) {
            MethodInfo methodInfo = MethodInfoThreadLocal.get();
            MethodReturnInfo returnInfo = methodInfo.getReturnInfo();
            // TODO : 处理返回值的逻辑
            doParseType(new Type[]{type}, builder, "", true, returnInfo, false);
        }
        METHOD_RETURN_MAP.put(methodName, builder.toString());
    }

  /*  private static void addRequestReturn2(MethodDoc method) throws Exception {
        // 方法名
        String methodName = method.qualifiedName();
        Type type = method.returnType();
        String typeName = type.qualifiedTypeName();
        StringBuilder builder = new StringBuilder();
        if (!BaseTypeUtil.isBaseType(typeName)) {
            // 先不考慮集合
            // 需要进行append的文字 TODO: 多层嵌套的问题
            Type[] types = getTypes(type);
            for (Type ty : types) {
                String tyName = ty.qualifiedTypeName();
                if (!BaseTypeUtil.isBaseType(tyName)) {
                    String modelText = JavaDocTextUtil.getModelText(tyName, "↪");
                    builder.append(modelText);
                }
            }
            METHOD_RETURN_MAP.put(methodName, builder.toString());
        } else {
            String comment = "结果";
            Tag[] returns = method.tags("return");
            if (returns != null || returns.length != 0) {
                comment = Arrays.stream(returns).map(Tag::text).collect(Collectors.joining(","));
            }
            // base type
            builder.append("|");
            builder.append("result");
            builder.append("|");
            // TODO: 改成根据是否有contranit注解，现在默认必须
            builder.append(" ");
            builder.append("|");
            builder.append(type.typeName());
            builder.append("|");
            builder.append(method.inlineTags());
            builder.append("|");
            builder.append("\r\n");
            METHOD_RETURN_MAP.put(methodName, builder.toString());
        }
    }*/

    private static void addRequestParam(MethodDoc method) throws Exception {
        // 方法名
        String methodName = method.qualifiedName();
        // 标签名
        ParamTag[] paramTags = method.paramTags();
        Map<String, String> baseCn = new HashMap<>(paramTags.length);
        for (ParamTag paramTag : paramTags) {
            String parameterName = paramTag.parameterName();
            String comment = paramTag.parameterComment();
            baseCn.put(parameterName, comment);
        }
        // 请求参数
        Parameter[] parameters = method.parameters();
        METHOD_REQ_PARAM_MAP.put(methodName, getRequestParamText(baseCn, parameters));
    }


    // 获取请求参数内容
    private static String getRequestParamText(Map<String, String> baseCn, Parameter[] parameters) throws Exception {
        StringBuilder builder = new StringBuilder();
        // | 名称|是否必须 |类型 |备注 |
        MethodInfo methodInfo = MethodInfoThreadLocal.get();
        for (Parameter parameter : parameters) {
            String typeName = parameter.type().qualifiedTypeName();
            // name
            String name = parameter.name();
            // comment
            String comment = baseCn.getOrDefault(name, " ");
            Type type = parameter.type();
            IParam paramByName = methodInfo.getParamByName(type.typeName());
            // base type
            builder.append("|");
            builder.append(name);
            builder.append("|");
            builder.append(paramByName == null ? "" : (paramByName.getConstraint() == null) ? "" : paramByName.getConstraint().toString());
            builder.append("|");
            builder.append(BaseTypeUtil.getActualTypeName(parameter.type()));
            builder.append("|");
            builder.append(comment);
            builder.append("|");
            builder.append("\r\n");
            // other 属性
            if (!BaseTypeUtil.isBaseType(typeName)) {
                // 需要进行append的文字
                // Type type = parameter.type();
                doParseType(new Type[]{type}, builder, null, true, methodInfo.getParamByName(type.typeName()), false);
            }
        }

        return builder.toString();
    }


    static void doParseType(Type[] types, StringBuilder builder, String delimit,
                            boolean noappend, IParam iParam, boolean newIParam) throws Exception {
        if (delimit == null) {
            delimit = "";
        }
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            // TODO 需要支持泛型
            ParameterizedType parameterizedType = type.asParameterizedType();
            if (parameterizedType != null) {
                Type[] typesArguments = parameterizedType.typeArguments();
                // 添加非基本类型
                if (!noappend) {
                    appendNotBase(builder, type, delimit);
                }
                // 递归 TODO 泛型 所以對於iparm需要重新确认
                doParseType(typesArguments,
                        builder,
                        delimit + PrefixMark.PREFIX,
                        false,
                        iParam, true);
            } else {
                String newDelimit = null;
                if (types.length > 1) {
                    if (delimit.length() == 0) {
                        newDelimit = PrefixMark.PREFIX + "[" + i + "] ";
                    } else {
                        newDelimit = delimit + "[" + i + "] ";
                    }
                } else if (types.length == 1) {
                    if (delimit.length() == 0) {
                        newDelimit = PrefixMark.PREFIX;
                    } else {
                        newDelimit = delimit;
                    }
                }

                IParam newParam;
                if (newIParam) {
                    newParam = iParam.getIparamByName(type.qualifiedTypeName());
                } else {
                    newParam = iParam;
                }
                // 结束
                if (!BaseTypeUtil.isBaseType(type.qualifiedTypeName())) {
                    // class model
                    checkCycleParse(type.qualifiedTypeName());
                    try {
                        // may cycle
                        String modelText = JavaDocTextUtil.getModelText(type.qualifiedTypeName(), newDelimit, newParam);
                        builder.append(modelText);
                    } finally {
                        // clear Set
                        clearCycleSet();
                    }
                } else {
                    // base type
                    appendSub(builder, type, newDelimit, newParam);
                }
            }
        }
    }

    private static void clearCycleSet() {
        CLASS_CYCLE_SET.clear();
    }

    private static void checkCycleParse(String qualifiedTypeName) {
        if (CLASS_CYCLE_SET.contains(qualifiedTypeName)) {
            throw new IllegalArgumentException("不支持解析循环引用的实体[" + qualifiedTypeName + "]");
        } else {
            CLASS_CYCLE_SET.add(qualifiedTypeName);
        }
    }

    private static void appendNotBase(StringBuilder builder, Type type, String delimit) {
        // base type
        builder.append("|");
        builder.append(delimit);
        builder.append(BaseTypeUtil.getActualTypeName(type));
        builder.append("|");
        builder.append("y");
        builder.append("|");
        builder.append(BaseTypeUtil.getActualTypeName(type));
        builder.append("|");
        builder.append(" ");
        builder.append("|");
        builder.append("\r\n");
    }

    private static void appendSub(StringBuilder builder, Type ty, String delimit, IParam newParam) {
        builder.append("|");
        builder.append(delimit);
        builder.append(ty.simpleTypeName());
        builder.append("|");
        builder.append(newParam == null ? "" : (newParam.getRequired()));
        builder.append("|");
        builder.append(ty.simpleTypeName());
        builder.append("|");
        builder.append(" ");
        builder.append("|");
        builder.append("\r\n");
    }

    private static Type[] getTypes(Type type) {
        // 泛型
        Type[] types = null;
        ParameterizedType parameterizedType = type.asParameterizedType();
        if (parameterizedType != null) {
            types = parameterizedType.typeArguments();
        } else {
            types = new Type[1];
            types = Arrays.asList(type).toArray(types);
        }
        return types;
    }

    private static void addPageTitle(MethodDoc method) {
        // 方法名
        String methodName = method.qualifiedName();
        // title 页面
        String title = method.commentText();
        if (title != null && title.trim().length() != 0) {
            METHOD_PAGE_TITLE_MAP.put(methodName, title);
        }
    }

    private static void addFolder(ClassDoc aClass) {
        // 文件夹名
        String title = aClass.commentText();
        // 类型名
        String typeName = aClass.qualifiedTypeName();
        if (title != null && title.trim().length() != 0) {
            TYPE_FOLDER_MAP.put(typeName, title);
        }
    }

    public static RootDoc getRoot() {
        return root;
    }

    public JavaDocReader() {

    }

    public static void parse(ControllerClassInfo methodInfo) throws Exception {
        ControllerClazzThreadLocal.set(methodInfo);
        Class<?> sourceClass = methodInfo.getControllerClazz();
        // workplace
        // String workplace = System.getProperty("user.dir");
        // classPatch
        String s = sourceClass.getResource("/").getPath();
        String classPath = s.substring(1, s.length() - 1);
        String workplace = classPath.replace("/target/classes", "");
        workplace = workplace.replace("/", "\\");
        // package 2 path
        String path = sourceClass.getTypeName().replace(".", "\\");
        com.sun.tools.javadoc.Main.execute(new String[]{"-doclet",
                Doclet.class.getName(),
                "-quiet",
                "-private",
                "-Xmaxerrs", "0",
                "-encoding", "utf-8",
                /* "-classpath",
                 classPath + sourceClass.getTypeName(),*/
// 获取单个代码文件FaceLogDefinition.java的javadoc
                workplace + "\\src\\main\\java\\" + path + ".java"});
        doParse();
    }

    // https://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#sourcepath
    public static void main(final String... args) throws Exception {
        // 调用com.sun.tools.javadoc.Main执行javadoc,参见 参考资料3
        // javadoc的调用参数，参见 参考资料1
        // -doclet 指定自己的docLet类名
        // -classpath 参数指定 源码文件及依赖库的class位置，不提供也可以执行，但无法获取到完整的注释信息(比如annotation)
        // -encoding 指定源码文件的编码格式
        com.sun.tools.javadoc.Main.execute(new String[]{"-doclet",
                Doclet.class.getName(),
// 因为自定义的Doclet类并不在外部jar中，就在当前类中，所以这里不需要指定-docletpath 参数，
//				"-docletpath",
//				Doclet.class.getResource("/").getPath(),
                "-quiet",
                "-private",
                "-Xmaxerrs", "1",
                "-encoding", "utf-8",
                "-classpath",
                "D:\\workspace\\hello-world-spring-boot\\target\\classes",
// 获取单个代码文件FaceLogDefinition.java的javadoc
                "D:\\workspace\\hello-world-spring-boot\\src\\main\\java\\com\\study\\h\\controller\\HelloController2.java"});
        //"D:\\workspace\\hello-world-spring-boot\\src\\main\\java\\com\\study\\h\\controller\\GreanStanardProjectDTO.java"});
        doParse();
    }
}