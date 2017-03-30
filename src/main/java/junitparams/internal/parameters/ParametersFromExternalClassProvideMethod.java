package junitparams.internal.parameters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.runners.model.FrameworkMethod;

import junitparams.NullType;
import junitparams.Parameters;
import junitparams.internal.parameters.toarray.ParamsToArrayConverter;

import static com.google.common.base.Preconditions.checkNotNull;

class ParametersFromExternalClassProvideMethod implements ParametrizationStrategy {

    private final FrameworkMethod frameworkMethod;
    private final Parameters annotation;

    ParametersFromExternalClassProvideMethod(FrameworkMethod frameworkMethod, Parameters annotation) {
        this.frameworkMethod = checkNotNull(frameworkMethod, "frameworkMethod must not be null");
        this.annotation = annotation;
    }

    @Override
    public Object[] getParameters() {
        Class<?> sourceClass = annotation.source();
        return fillResultWithAllParamProviderMethods(sourceClass);
    }

    @Override
    public boolean isApplicable() {
        return annotation != null
                && !annotation.source().isAssignableFrom(NullType.class)
                && annotation.method().isEmpty();
    }

    private Object[] fillResultWithAllParamProviderMethods(Class<?> sourceClass) {
        if (sourceClass.isEnum()) {
            return sourceClass.getEnumConstants();
        }

        List<Object> result = getParamsFromSourceHierarchy(sourceClass);
        if (result.isEmpty())
            throw new RuntimeException(
                    "No methods starting with provide or they return no result in the parameters source class: "
                            + sourceClass.getName());

        return result.toArray();
    }

    private List<Object> getParamsFromSourceHierarchy(Class<?> sourceClass) {
        List<Object> result = new ArrayList<Object>();
        while (sourceClass.getSuperclass() != null) {
            result.addAll(gatherParamsFromAllMethodsFrom(sourceClass));
            sourceClass = sourceClass.getSuperclass();
        }

        return result;
    }

    private List<Object> gatherParamsFromAllMethodsFrom(Class<?> sourceClass) {
        List<Object> result = new ArrayList<Object>();
        Method[] methods = sourceClass.getDeclaredMethods();
        for (Method prividerMethod : methods) {
            if (prividerMethod.getName().startsWith("provide")) {
                if (!Modifier.isStatic(prividerMethod.getModifiers())) {
                    throw new RuntimeException("Parameters source method " +
                            prividerMethod.getName() +
                            " is not declared as static. Change it to a static method.");
                }
                try {
                    result.addAll(getDataFromMethod(prividerMethod));
                } catch (Exception e) {
                    throw new RuntimeException("Cannot invoke parameters source method: " + prividerMethod.getName(),
                            e);
                }
            }
        }
        return result;
    }

    private List<Object> getDataFromMethod(Method prividerMethod) throws IllegalAccessException, InvocationTargetException {
        Object result = prividerMethod.invoke(null);
        ImmutableParameterTypeSupplier parameterTypeSupplier =
                ImmutableParameterTypeSupplier.of(frameworkMethod.getMethod().getParameterTypes());
        Object[] resultsArray = new ParamsToArrayConverter(parameterTypeSupplier).convert(result);
        return Arrays.asList(resultsArray);
    }
}
