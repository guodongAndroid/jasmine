package com.guodong.jasmine.compiler;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import java.lang.reflect.Method;
import java.util.StringJoiner;

/**
 * Created by guodongAndroid on 2024/5/24.
 */
public abstract class BaseProcessor extends AbstractProcessor {

    protected Messager messager;

    protected JavacTrees trees;

    protected TreeMaker treeMaker;

    protected Names names;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        processingEnv = jbUnwrap(ProcessingEnvironment.class, processingEnv);
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    protected void log(String tag, CharSequence... message) {
        StringJoiner joiner = new StringJoiner(", ", tag + ": ", "");
        for (CharSequence sc : message) {
            joiner.add(sc);
        }
        messager.printMessage(Diagnostic.Kind.NOTE, joiner.toString());
    }

    private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
        T unwrapped = null;
        try {
            final Class<?> apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            final Method unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
        } catch (Throwable ignored) {
        }
        return unwrapped != null ? unwrapped : wrapper;
    }
}
