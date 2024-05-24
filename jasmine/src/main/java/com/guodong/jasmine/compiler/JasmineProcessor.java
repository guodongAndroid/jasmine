package com.guodong.jasmine.compiler;

import com.google.auto.service.AutoService;
import com.guodong.jasmine.Ignore;
import com.guodong.jasmine.Jasmine;
import com.guodong.jasmine.utils.StringUtils;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.*;

import static com.guodong.jasmine.compiler.Constants.Java.isLegalNaming;
import static com.guodong.jasmine.compiler.Constants.MP.FN_TABLE_FIELD;
import static com.guodong.jasmine.compiler.Constants.MP.VALUE_LITERAL;

/**
 * Created by guodongAndroid on 2024/5/24.
 */
@AutoService(Processor.class)
public class JasmineProcessor extends BaseProcessor {

    private static final String TAG = JasmineProcessor.class.getSimpleName();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new TreeSet<>(Collections.singletonList(Jasmine.class.getCanonicalName()));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Jasmine.class);
        for (Element element : elements) {
            Jasmine annotation = element.getAnnotation(Jasmine.class);
            boolean enabled = annotation.enabled();
            if (!enabled) {
                continue;
            }

            boolean sqlEscaping = annotation.sqlEscaping();
            boolean useOriginalVariableName = annotation.useOriginalVariableName();

            JCTree tree = trees.getTree(element);
            tree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    String className = jcClassDecl.name.toString();
                    log(TAG, "@Jasmine process [" + className + "] begin");

                    ListBuffer<JCTree.JCVariableDecl> jcVariableDeclList = new ListBuffer<>();
                    ListBuffer<String> staticFinalConstNames = new ListBuffer<>();

                    for (JCTree jcTree : jcClassDecl.defs) {
                        if (jcTree.getKind().equals(Tree.Kind.VARIABLE)) {
                            JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl) jcTree;
                            Set<Modifier> flags = jcVariableDecl.mods.getFlags();
                            if (flags.contains(Modifier.STATIC) && flags.contains(Modifier.FINAL)) {
                                // log(TAG, "静态：" + ((JCTree.JCIdent) jcVariableDecl.getType()).name.toString() + ", " + ((JCTree.JCLiteral) jcVariableDecl.getInitializer()).typetag);
                                staticFinalConstNames.append(jcVariableDecl.name.toString());
                                continue;
                            }

                            jcVariableDeclList.append(jcVariableDecl);
                        }
                    }

                    /*for (String name : staticFinalConstNames) {
                        log(TAG, "[" + className + "] 有静态常量: " + name);
                    }

                    for (JCTree.JCVariableDecl decl : jcVariableDeclList) {
                        log(TAG, "[" + className + "] 有成员变量: " + decl.name.toString());
                    }*/

                    for (JCTree.JCVariableDecl decl : jcVariableDeclList) {
                        String variableName = decl.name.toString();
                        String upperCaseVariableName = variableName.toUpperCase(Locale.ENGLISH);
                        if (staticFinalConstNames.contains(upperCaseVariableName)) {
                            log(TAG, "已经存在 [" + upperCaseVariableName + "] 的常量, 跳过");
                            continue;
                        }

                        // 字段上有 Ignore 注解, 忽略此字段
                        if (hasIgnoreAnnotation(decl)) {
                            log(TAG, "字段 [" + variableName + "] 上存在 「@com.guodong,jasmine.Ignore」注解, 忽略" );
                            continue;
                        }

                        String mpTableFieldValue = getMPTableFieldValueOrNull(decl);

                        JCTree.JCModifiers modifiers = treeMaker.Modifiers(Flags.PUBLIC | Flags.STATIC | Flags.FINAL);
                        Name constName = generateConstName(useOriginalVariableName, upperCaseVariableName, mpTableFieldValue);
                        JCTree.JCIdent constType = treeMaker.Ident(names.fromString("String"));
                        String constInitializeValue = generateConstInitializeValue(sqlEscaping, variableName, mpTableFieldValue);
                        JCTree.JCLiteral constInitializer = treeMaker.Literal(constInitializeValue);
                        JCTree.JCVariableDecl constant = treeMaker.VarDef(modifiers, constName, constType, constInitializer);

                        jcClassDecl.defs = jcClassDecl.defs.prepend(constant);
                    }

                    log(TAG, "@Jasmine process [" + className + "] end");

                    super.visitClassDef(jcClassDecl);
                }
            });
        }

        return true;
    }

    private Name generateConstName(boolean useOriginalVariableName, String upperCaseVariableName, String mpTableFieldValue) {
        String name = upperCaseVariableName;
        if (!useOriginalVariableName && Objects.nonNull(mpTableFieldValue) && isLegalNaming(mpTableFieldValue)) {
            name = mpTableFieldValue.toUpperCase(Locale.ENGLISH);
        }
        return names.fromString(name);
    }

    private String generateConstInitializeValue(boolean sqlEscaping, String variableName, String mpTableFieldValue) {
        variableName = mpTableFieldValue != null ? mpTableFieldValue : variableName;
        return sqlEscaping ? String.format(Locale.ENGLISH, "`%s`", variableName) : variableName;
    }

    private boolean hasIgnoreAnnotation(JCTree.JCVariableDecl decl) {
        final boolean[] hasIgnoreAnnotation = {false};
        decl.accept(new TreeTranslator() {
            @Override
            public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                Type annotationType = jcAnnotation.type;
                String annotationFqName = annotationType.toString();
                hasIgnoreAnnotation[0] = StringUtils.equals(Ignore.class.getCanonicalName(), annotationFqName);
                super.visitAnnotation(jcAnnotation);
            }
        });

        return hasIgnoreAnnotation[0];
    }

    private String getMPTableFieldValueOrNull(JCTree.JCVariableDecl decl) {
        final String[] mpTableFieldValue = {null};
        decl.accept(new TreeTranslator() {
            @Override
            public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
                Type annotationType = jcAnnotation.type;
                String annotationFqName = annotationType.toString();
                List<JCTree.JCExpression> argExpressions = jcAnnotation.args;

                if (FN_TABLE_FIELD.equals(annotationFqName)) {
                    for (JCTree.JCExpression expression : argExpressions) {
                        if (expression.getKind().equals(Tree.Kind.ASSIGNMENT)) {
                            JCTree.JCAssign assign = (JCTree.JCAssign) expression;
                            JCTree.JCExpression lhs = assign.lhs;
                            JCTree.JCExpression rhs = assign.rhs;
                            if (lhs.getKind().equals(Tree.Kind.IDENTIFIER)) {
                                JCTree.JCIdent lhsIdent = (JCTree.JCIdent) lhs;
                                String lhsName = lhsIdent.name.toString();
                                if (VALUE_LITERAL.equals(lhsName) && rhs.getKind().equals(Tree.Kind.STRING_LITERAL)) {
                                    JCTree.JCLiteral rhsLiteral = (JCTree.JCLiteral) rhs;
                                    mpTableFieldValue[0] = rhsLiteral.value.toString();
                                }
                            }
                        }
                    }
                }

                super.visitAnnotation(jcAnnotation);
            }
        });
        return mpTableFieldValue[0];
    }
}
