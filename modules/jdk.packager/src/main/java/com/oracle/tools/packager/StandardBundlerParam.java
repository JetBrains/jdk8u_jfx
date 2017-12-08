/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.tools.packager;

import jdk.packager.internal.legacy.JLinkBundlerHelper;

import com.sun.javafx.tools.packager.bundlers.BundleParams;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static jdk.packager.internal.legacy.JLinkBundlerHelper.findPathOfModule;
import static jdk.packager.internal.legacy.JLinkBundlerHelper.listOfPathToString;

/**
 * @deprecated use {@link ToolProvider} to locate the {@code "javapackager"} tool instead.
 */
@Deprecated(since="10", forRemoval=true)
public class StandardBundlerParam<T> extends BundlerParamInfo<T> {

    public static final String MANIFEST_JAVAFX_MAIN ="JavaFX-Application-Class";
    public static final String MANIFEST_PRELOADER = "JavaFX-Preloader-Class";

    private static final ResourceBundle I18N =
            ResourceBundle.getBundle(StandardBundlerParam.class.getName());

    public StandardBundlerParam(String name, String description, String id,
                                Class<T> valueType,
                                Function<Map<String, ? super Object>, T> defaultValueFunction,
                                BiFunction<String, Map<String, ? super Object>, T> stringConverter) {
        this.name = name;
        this.description = description;
        this.id = id;
        this.valueType = valueType;
        this.defaultValueFunction = defaultValueFunction;
        this.stringConverter = stringConverter;
    }

    public static final StandardBundlerParam<RelativeFileSet> APP_RESOURCES =
            new StandardBundlerParam<>(
                    I18N.getString("param.app-resources.name"),
                    I18N.getString("param.app-resource.description"),
                    BundleParams.PARAM_APP_RESOURCES,
                    RelativeFileSet.class,
                    null, // no default.  Required parameter
                    null // no string translation, tool must provide complex type
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<RelativeFileSet>> APP_RESOURCES_LIST =
            new StandardBundlerParam<>(
                    I18N.getString("param.app-resources-list.name"),
                    I18N.getString("param.app-resource-list.description"),
                    BundleParams.PARAM_APP_RESOURCES + "List",
                    (Class<List<RelativeFileSet>>) (Object) List.class,
                    p -> new ArrayList<>(Collections.singletonList(APP_RESOURCES.fetchFrom(p))), // Default is appResources, as a single item list
                    StandardBundlerParam::createAppResourcesListFromString
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<String> SOURCE_DIR =
            new StandardBundlerParam<>(
                    I18N.getString("param.source-dir.name"),
                    I18N.getString("param.source-dir.description"),
                    "srcdir",
                    String.class,
                    p -> null,
                    (s, p) -> {
                        String value = String.valueOf(s);
                        if (value.charAt(value.length() - 1) == File.separatorChar) {
                            return value.substring(0, value.length() - 1);
                        }
                        else {
                            return value;
                        }
                    }
            );

    // note that each bundler is likely to replace this one with their own converter
    public static final StandardBundlerParam<RelativeFileSet> MAIN_JAR =
            new StandardBundlerParam<>(
                    I18N.getString("param.main-jar.name"),
                    I18N.getString("param.main-jar.description"),
                    "mainJar",
                    RelativeFileSet.class,
                    params -> {
                        extractMainClassInfoFromAppResources(params);
                        return (RelativeFileSet) params.get("mainJar");
                    },
                    (s, p) -> getMainJar(s, p)
            );

    public static final StandardBundlerParam<String> CLASSPATH =
            new StandardBundlerParam<>(
                    I18N.getString("param.classpath.name"),
                    I18N.getString("param.classpath.description"),
                    "classpath",
                    String.class,
                    params -> {
                        extractMainClassInfoFromAppResources(params);
                        String cp = (String) params.get("classpath");
                        return cp == null ? "" : cp;
                    },
                    (s, p) -> s.replace(File.pathSeparator, " ")
            );

    public static final StandardBundlerParam<String> MAIN_CLASS =
            new StandardBundlerParam<>(
                    I18N.getString("param.main-class.name"),
                    I18N.getString("param.main-class.description"),
                    BundleParams.PARAM_APPLICATION_CLASS,
                    String.class,
                    params -> {
                        extractMainClassInfoFromAppResources(params);
                        String s = (String) params.get(BundleParams.PARAM_APPLICATION_CLASS);
                        if (s == null) {
                            s = JLinkBundlerHelper.getMainClass(params);
                        }
                        return s;
                    },
                    (s, p) -> s
            );

    public static final StandardBundlerParam<String> APP_NAME =
            new StandardBundlerParam<>(
                    I18N.getString("param.app-name.name"),
                    I18N.getString("param.app-name.description"),
                    BundleParams.PARAM_NAME,
                    String.class,
                    params -> {
                        String s = MAIN_CLASS.fetchFrom(params);
                        if (s == null) return null;

                        int idx = s.lastIndexOf(".");
                        if (idx >= 0) {
                            return s.substring(idx+1);
                        }
                        return s;
                    },
                    (s, p) -> s
            );

    private static Pattern TO_FS_NAME = Pattern.compile("\\s|[\\\\/?:*<>|]"); // keep out invalid/undesireable filename characters

    public static final StandardBundlerParam<String> APP_FS_NAME =
            new StandardBundlerParam<>(
                    I18N.getString("param.app-fs-name.name"),
                    I18N.getString("param.app-fs-name.description"),
                    "name.fs",
                    String.class,
                    params -> TO_FS_NAME.matcher(APP_NAME.fetchFrom(params)).replaceAll(""),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<File> ICON =
            new StandardBundlerParam<>(
                    I18N.getString("param.icon-file.name"),
                    I18N.getString("param.icon-file.description"),
                    BundleParams.PARAM_ICON,
                    File.class,
                    params -> null,
                    (s, p) -> new File(s)
            );

    public static final StandardBundlerParam<String> VENDOR =
            new StandardBundlerParam<>(
                    I18N.getString("param.vendor.name"),
                    I18N.getString("param.vendor.description"),
                    BundleParams.PARAM_VENDOR,
                    String.class,
                    params -> I18N.getString("param.vendor.default"),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<String> CATEGORY =
            new StandardBundlerParam<>(
                    I18N.getString("param.category.name"),
                    I18N.getString("param.category.description"),
                    BundleParams.PARAM_CATEGORY,
                    String.class,
                    params -> I18N.getString("param.category.default"),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<String> DESCRIPTION =
            new StandardBundlerParam<>(
                    I18N.getString("param.description.name"),
                    I18N.getString("param.description.description"),
                    BundleParams.PARAM_DESCRIPTION,
                    String.class,
                    params -> params.containsKey(APP_NAME.getID())
                            ? APP_NAME.fetchFrom(params)
                            : I18N.getString("param.description.default"),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<String> COPYRIGHT =
            new StandardBundlerParam<>(
                    I18N.getString("param.copyright.name"),
                    I18N.getString("param.copyright.description"),
                    BundleParams.PARAM_COPYRIGHT,
                    String.class,
                    params -> MessageFormat.format(I18N.getString("param.copyright.default"), new Date()),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<Boolean> USE_FX_PACKAGING =
            new StandardBundlerParam<>(
                    I18N.getString("param.use-javafx-packaging.name"),
                    I18N.getString("param.use-javafx-packaging.description"),
                    "fxPackaging",
                    Boolean.class,
                    params -> {
                        extractMainClassInfoFromAppResources(params);
                        Boolean result = (Boolean) params.get("fxPackaging");
                        return (result == null) ? Boolean.FALSE : result;
                    },
                    (s, p) -> Boolean.valueOf(s)
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> ARGUMENTS =
            new StandardBundlerParam<>(
                    I18N.getString("param.arguments.name"),
                    I18N.getString("param.arguments.description"),
                    "arguments",
                    (Class<List<String>>) (Object) List.class,
                    params -> Collections.emptyList(),
                    (s, p) -> splitStringWithEscapes(s)
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> JVM_OPTIONS =
            new StandardBundlerParam<>(
                    I18N.getString("param.jvm-options.name"),
                    I18N.getString("param.jvm-options.description"),
                    "jvmOptions",
                    (Class<List<String>>) (Object) List.class,
                    params -> Collections.emptyList(),
                    (s, p) -> Arrays.asList(s.split("\\s+"))
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<Map<String, String>> JVM_PROPERTIES =
            new StandardBundlerParam<>(
                    I18N.getString("param.jvm-system-properties.name"),
                    I18N.getString("param.jvm-system-properties.description"),
                    "jvmProperties",
                    (Class<Map<String, String>>) (Object) Map.class,
                    params -> Collections.emptyMap(),
                    (s, params) -> {
                        Map<String, String> map = new HashMap<>();
                        try {
                            Properties p = new Properties();
                            p.load(new StringReader(s));
                            for (Map.Entry<Object, Object> entry : p.entrySet()) {
                                map.put((String)entry.getKey(), (String)entry.getValue());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return map;
                    }
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<Map<String, String>> USER_JVM_OPTIONS =
            new StandardBundlerParam<>(
                    I18N.getString("param.user-jvm-options.name"),
                    I18N.getString("param.user-jvm-options.description"),
                    "userJvmOptions",
                    (Class<Map<String, String>>) (Object) Map.class,
                    params -> Collections.emptyMap(),
                    (s, params) -> {
                        Map<String, String> map = new HashMap<>();
                        try {
                            Properties p = new Properties();
                            p.load(new StringReader(s));
                            for (Map.Entry<Object, Object> entry : p.entrySet()) {
                                map.put((String)entry.getKey(), (String)entry.getValue());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return map;
                    }
            );

    public static final StandardBundlerParam<String> TITLE =
            new StandardBundlerParam<>(
                    I18N.getString("param.title.name"),
                    I18N.getString("param.title.description"), //?? but what does it do?
                    BundleParams.PARAM_TITLE,
                    String.class,
                    APP_NAME::fetchFrom,
                    (s, p) -> s
            );

    // note that each bundler is likely to replace this one with their own converter
    public static final StandardBundlerParam<String> VERSION =
            new StandardBundlerParam<>(
                    I18N.getString("param.version.name"),
                    I18N.getString("param.version.description"),
                    BundleParams.PARAM_VERSION,
                    String.class,
                    params -> I18N.getString("param.version.default"),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<Boolean> SYSTEM_WIDE =
            new StandardBundlerParam<>(
                    I18N.getString("param.system-wide.name"),
                    I18N.getString("param.system-wide.description"),
                    BundleParams.PARAM_SYSTEM_WIDE,
                    Boolean.class,
                    params -> null,
                    // valueOf(null) is false, and we actually do want null in some cases
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? null : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> SERVICE_HINT  =
            new StandardBundlerParam<>(
                    I18N.getString("param.service-hint.name"),
                    I18N.getString("param.service-hint.description"),
                    BundleParams.PARAM_SERVICE_HINT,
                    Boolean.class,
                    params -> false,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? false : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> START_ON_INSTALL  =
            new StandardBundlerParam<>(
                    I18N.getString("param.start-on-install.name"),
                    I18N.getString("param.start-on-install.description"),
                    "startOnInstall",
                    Boolean.class,
                    params -> false,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? false : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> STOP_ON_UNINSTALL  =
            new StandardBundlerParam<>(
                    I18N.getString("param.stop-on-uninstall.name"),
                    I18N.getString("param.stop-on-uninstall.description"),
                    "stopOnUninstall",
                    Boolean.class,
                    params -> true,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? true : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> RUN_AT_STARTUP  =
            new StandardBundlerParam<>(
                    I18N.getString("param.run-at-startup.name"),
                    I18N.getString("param.run-at-startup.description"),
                    "runAtStartup",
                    Boolean.class,
                    params -> false,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? false : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> SIGN_BUNDLE  =
            new StandardBundlerParam<>(
                    I18N.getString("param.sign-bundle.name"),
                    I18N.getString("param.sign-bundle.description"),
                    "signBundle",
                    Boolean.class,
                    params -> null,
                    // valueOf(null) is false, and we actually do want null in some cases
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? null : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> SHORTCUT_HINT =
            new StandardBundlerParam<>(
                    I18N.getString("param.desktop-shortcut-hint.name"),
                    I18N.getString("param.desktop-shortcut-hint.description"),
                    BundleParams.PARAM_SHORTCUT,
                    Boolean.class,
                    params -> false,
                    // valueOf(null) is false, and we actually do want null in some cases
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? false : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> MENU_HINT =
            new StandardBundlerParam<>(
                    I18N.getString("param.menu-shortcut-hint.name"),
                    I18N.getString("param.menu-shortcut-hint.description"),
                    BundleParams.PARAM_MENU,
                    Boolean.class,
                    params -> false,
                    // valueOf(null) is false, and we actually do want null in some cases
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? true : Boolean.valueOf(s)
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> LICENSE_FILE =
            new StandardBundlerParam<>(
                    I18N.getString("param.license-file.name"),
                    I18N.getString("param.license-file.description"),
                    BundleParams.PARAM_LICENSE_FILE,
                    (Class<List<String>>)(Object)List.class,
                    params -> Collections.<String>emptyList(),
                    (s, p) -> Arrays.asList(s.split(","))
            );

    public static final BundlerParamInfo<String> LICENSE_TYPE =
            new StandardBundlerParam<>(
                    I18N.getString("param.license-type.name"),
                    I18N.getString("param.license-type.description"),
                    BundleParams.PARAM_LICENSE_TYPE,
                    String.class,
                    params -> I18N.getString("param.license-type.default"),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<File> BUILD_ROOT =
            new StandardBundlerParam<>(
                    I18N.getString("param.build-root.name"),
                    I18N.getString("param.build-root.description"),
                    "buildRoot",
                    File.class,
                    params -> {
                        try {
                            return Files.createTempDirectory("fxbundler").toFile();
                        } catch (IOException ioe) {
                            return null;
                        }
                    },
                    (s, p) -> new File(s)
            );

    public static final StandardBundlerParam<String> IDENTIFIER =
            new StandardBundlerParam<>(
                    I18N.getString("param.identifier.name"),
                    I18N.getString("param.identifier.description"),
                    BundleParams.PARAM_IDENTIFIER,
                    String.class,
                    params -> {
                        String s = MAIN_CLASS.fetchFrom(params);
                        if (s == null) return null;

                        int idx = s.lastIndexOf(".");
                        if (idx >= 1) {
                            return s.substring(0, idx);
                        }
                        return s;
                    },
                    (s, p) -> s
            );

    public static final StandardBundlerParam<String> PREFERENCES_ID =
            new StandardBundlerParam<>(
                    I18N.getString("param.preferences-id.name"),
                    I18N.getString("param.preferences-id.description"),
                    "preferencesID",
                    String.class,
                    p -> Optional.ofNullable(IDENTIFIER.fetchFrom(p)).orElse("").replace('.', '/'),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<String> PRELOADER_CLASS =
            new StandardBundlerParam<>(
                    I18N.getString("param.preloader.name"),
                    I18N.getString("param.preloader.description"),
                    "preloader",
                    String.class,
                    p -> null,
                    null
            );

    public static final StandardBundlerParam<Boolean> VERBOSE  =
            new StandardBundlerParam<>(
                    I18N.getString("param.verbose.name"),
                    I18N.getString("param.verbose.description"),
                    "verbose",
                    Boolean.class,
                    params -> false,
                    // valueOf(null) is false, and we actually do want null in some cases
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? true : Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<File> DROP_IN_RESOURCES_ROOT =
            new StandardBundlerParam<>(
                    I18N.getString("param.drop-in-resources-root.name"),
                    I18N.getString("param.drop-in-resources-root.description"),
                    "dropinResourcesRoot",
                    File.class,
                    params -> null,
                    (s, p) -> new File(s)
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<Map<String, ? super Object>>> SECONDARY_LAUNCHERS =
            new StandardBundlerParam<>(
                    I18N.getString("param.secondary-launchers.name"),
                    I18N.getString("param.secondary-launchers.description"),
                    "secondaryLaunchers",
                    (Class<List<Map<String, ? super Object>>>) (Object) List.class,
                    params -> new ArrayList<>(1),
                    // valueOf(null) is false, and we actually do want null in some cases
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<Map<String, ? super Object>>> FILE_ASSOCIATIONS =
            new StandardBundlerParam<>(
                    I18N.getString("param.file-associations.name"),
                    I18N.getString("param.file-associations.description"),
                    "fileAssociations",
                    (Class<List<Map<String, ? super Object>>>) (Object) List.class,
                    params -> new ArrayList<>(1),
                    // valueOf(null) is false, and we actually do want null in some cases
                    (s, p) -> null
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> FA_EXTENSIONS =
            new StandardBundlerParam<>(
                    I18N.getString("param.fa-extension.name"),
                    I18N.getString("param.fa-extension.description"),
                    "fileAssociation.extension",
                    (Class<List<String>>) (Object) List.class,
                    params -> null, // null means not matched to an extension
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> FA_CONTENT_TYPE =
            new StandardBundlerParam<>(
                    I18N.getString("param.fa-content-type.name"),
                    I18N.getString("param.fa-content-type.description"),
                    "fileAssociation.contentType",
                    (Class<List<String>>) (Object) List.class,
                    params -> null, // null means not matched to a content/mime type
                    (s, p) -> Arrays.asList(s.split("(,|\\s)+"))
            );

    public static final StandardBundlerParam<String> FA_DESCRIPTION =
            new StandardBundlerParam<>(
                    I18N.getString("param.fa-description.name"),
                    I18N.getString("param.fa-description.description"),
                    "fileAssociation.description",
                    String.class,
                    params -> APP_NAME.fetchFrom(params) + " File",
                    null
            );

    public static final StandardBundlerParam<File> FA_ICON =
            new StandardBundlerParam<>(
                    I18N.getString("param.fa-icon.name"),
                    I18N.getString("param.fa-icon.description"),
                    "fileAssociation.icon",
                    File.class,
                    ICON::fetchFrom,
                    (s, p) -> new File(s)
            );

    public static final StandardBundlerParam<Boolean> UNLOCK_COMMERCIAL_FEATURES =
            new StandardBundlerParam<>(
                    I18N.getString("param.commercial-features.name"),
                    I18N.getString("param.commercial-features.description"),
                    "commercialFeatures",
                    Boolean.class,
                    p -> false,
                    (s, p) -> Boolean.parseBoolean(s)
            );

    public static final StandardBundlerParam<Boolean> ENABLE_APP_CDS =
            new StandardBundlerParam<>(
                    I18N.getString("param.com-app-cds.name"),
                    I18N.getString("param.com-app-cds.description"),
                    "commercial.AppCDS",
                    Boolean.class,
                    p -> false,
                    (s, p) -> Boolean.parseBoolean(s)
            );

    public static final StandardBundlerParam<String> APP_CDS_CACHE_MODE =
            new StandardBundlerParam<>(
                    I18N.getString("param.com-app-cds-cache-mode.name"),
                    I18N.getString("param.com-app-cds-cache-mode.description"),
                    "commercial.AppCDS.cache",
                    String.class,
                    p -> "auto",
                    (s, p) -> s
            );

    @SuppressWarnings("unchecked")
    public static final StandardBundlerParam<List<String>> APP_CDS_CLASS_ROOTS =
            new StandardBundlerParam<>(
                    I18N.getString("param.com-app-cds-root.name"),
                    I18N.getString("param.com-app-cds-root.description"),
                    "commercial.AppCDS.classRoots",
                    (Class<List<String>>)((Object)List.class),
                    p -> Collections.singletonList(MAIN_CLASS.fetchFrom(p)),
                    (s, p) -> Arrays.asList(s.split("[ ,:]"))
            );
    private static final String JAVABASEJMOD = "java.base.jmod";

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<List<Path>> MODULE_PATH =
            new StandardBundlerParam<>(
                    I18N.getString("param.module-path.name"),
                    I18N.getString("param.module-path.description"),
                    "module-path",
                    (Class<List<Path>>) (Object)List.class,
                    p -> { return getDefaultModulePath(); },
                    (s, p) -> {
                        List<Path> modulePath = Arrays.asList(s.split(File.pathSeparator)).stream()
                                                      .map(ss -> new File(ss).toPath())
                                                      .collect(Collectors.toList());
                        Path javaBasePath = null;
                        if (modulePath != null) {
                            javaBasePath = JLinkBundlerHelper.findPathOfModule(modulePath, JAVABASEJMOD);
                        }
                        else {
                            modulePath = new ArrayList();
                        }

                        // Add the default JDK module path to the module path.
                        if (javaBasePath == null) {
                            List<Path> jdkModulePath = getDefaultModulePath();

                            if (jdkModulePath != null) {
                                modulePath.addAll(jdkModulePath);
                                javaBasePath = JLinkBundlerHelper.findPathOfModule(modulePath, JAVABASEJMOD);
                            }
                        }

                        if (javaBasePath == null || !Files.exists(javaBasePath)) {
                            com.oracle.tools.packager.Log.info(
                                String.format(I18N.getString("warning.no.jdk.modules.found")));
                        }

                        return modulePath;
                    });

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<String> MODULE =
            new StandardBundlerParam<>(
                    I18N.getString("param.main.module.name"),
                    I18N.getString("param.main.module.description"),
                    "module",
                    String.class,
                    p -> null,
                    (s, p) -> {
                        return String.valueOf(s);
                    });

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<Set<String>> ADD_MODULES =
            new StandardBundlerParam<>(
                    I18N.getString("param.add-modules.name"),
                    I18N.getString("param.add-modules.description"),
                    "add-modules",
                    (Class<Set<String>>) (Object) Set.class,
                    p -> new LinkedHashSet(),
                    (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(",")))
            );

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<Set<String>> LIMIT_MODULES =
            new StandardBundlerParam<>(
                    I18N.getString("param.limit-modules.name"),
                    I18N.getString("param.limit-modules.description"),
                    "limit-modules",
                    (Class<Set<String>>) (Object) Set.class,
                    p -> new LinkedHashSet(),
                    (s, p) -> new LinkedHashSet<>(Arrays.asList(s.split(",")))
            );

    @SuppressWarnings("unchecked")
    public static final BundlerParamInfo<Boolean> STRIP_NATIVE_COMMANDS =
            new StandardBundlerParam<>(
                    I18N.getString("param.strip-executables.name"),
                    I18N.getString("param.strip-executables.description"),
                    "strip-native-commands",
                    Boolean.class,
                    p -> Boolean.TRUE,
                    (s, p) -> Boolean.valueOf(s)
            );

    public static final BundlerParamInfo<Boolean> SINGLETON = new StandardBundlerParam<> (
        I18N.getString("param.singleton.name"),
        I18N.getString("param.singleton.description"),
        BundleParams.PARAM_SINGLETON,
        Boolean.class,
        params -> Boolean.FALSE,
        (s, p) -> Boolean.valueOf(s)
    );

    public static void extractMainClassInfoFromAppResources(Map<String, ? super Object> params) {
        boolean hasMainClass = params.containsKey(MAIN_CLASS.getID());
        boolean hasMainJar = params.containsKey(MAIN_JAR.getID());
        boolean hasMainJarClassPath = params.containsKey(CLASSPATH.getID());
        boolean hasPreloader = params.containsKey(PRELOADER_CLASS.getID());
        boolean hasModule = params.containsKey(MODULE.getID());

        if (hasMainClass && hasMainJar && hasMainJarClassPath || hasModule) {
            return;
        }

        // it's a pair.  The [0] is the srcdir [1] is the file relative to sourcedir
        List<String[]> filesToCheck = new ArrayList<>();

        if (hasMainJar) {
            RelativeFileSet rfs = MAIN_JAR.fetchFrom(params);
            for (String s : rfs.getIncludedFiles()) {
                filesToCheck.add(new String[]{rfs.getBaseDirectory().toString(), s});
            }
        } else if (hasMainJarClassPath) {
            for (String s : CLASSPATH.fetchFrom(params).split("\\s+")) {
                if (APP_RESOURCES.fetchFrom(params) != null) {
                    filesToCheck.add(new String[] {APP_RESOURCES.fetchFrom(params).getBaseDirectory().toString(), s});
                }
            }
        } else {
            List<RelativeFileSet> rfsl = APP_RESOURCES_LIST.fetchFrom(params);
            if (rfsl == null || rfsl.isEmpty()) {
                return;
            }
            for (RelativeFileSet rfs : rfsl) {
                if (rfs == null) continue;

                for (String s : rfs.getIncludedFiles()) {
                    filesToCheck.add(new String[]{rfs.getBaseDirectory().toString(), s});
                }
            }
        }

        String declaredMainClass = (String) params.get(MAIN_CLASS.getID());

        // presume the set iterates in-order
        for (String[] fnames : filesToCheck) {
            try {
                // only sniff jars
                if (!fnames[1].toLowerCase().endsWith(".jar")) continue;

                File file = new File(fnames[0], fnames[1]);
                // that actually exist
                if (!file.exists()) continue;

                try (JarFile jf = new JarFile(file)) {
                    Manifest m = jf.getManifest();
                    Attributes attrs = (m != null) ? m.getMainAttributes() : null;

                    if (attrs != null) {
                        String mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);
                        String fxMain = attrs.getValue(MANIFEST_JAVAFX_MAIN);
                        String preloaderClass = attrs.getValue(MANIFEST_PRELOADER);
                        if (hasMainClass) {
                            if (declaredMainClass.equals(fxMain)) {
                                params.put(USE_FX_PACKAGING.getID(), true);
                            } else if (declaredMainClass.equals(mainClass)) {
                                params.put(USE_FX_PACKAGING.getID(), false);
                            } else {
                                if (fxMain != null) {
                                    Log.info(MessageFormat.format(I18N.getString("message.fx-app-does-not-match-specified-main"), fnames[1], fxMain, declaredMainClass));
                                }
                                if (mainClass != null) {
                                    Log.info(MessageFormat.format(I18N.getString("message.main-class-does-not-match-specified-main"), fnames[1], mainClass, declaredMainClass));
                                }
                                continue;
                            }
                        } else {
                            if (fxMain != null) {
                                params.put(USE_FX_PACKAGING.getID(), true);
                                params.put(MAIN_CLASS.getID(), fxMain);
                            } else if (mainClass != null) {
                                params.put(USE_FX_PACKAGING.getID(), false);
                                params.put(MAIN_CLASS.getID(), mainClass);
                            } else {
                                continue;
                            }
                        }
                        if (!hasPreloader && preloaderClass != null) {
                            params.put(PRELOADER_CLASS.getID(), preloaderClass);
                        }
                        if (!hasMainJar) {
                            if (fnames[0] == null) {
                                fnames[0] = file.getParentFile().toString();
                            }
                            params.put(MAIN_JAR.getID(), new RelativeFileSet(new File(fnames[0]), new LinkedHashSet<>(Collections.singletonList(file))));
                        }
                        if (!hasMainJarClassPath) {
                            String cp = attrs.getValue(Attributes.Name.CLASS_PATH);
                            params.put(CLASSPATH.getID(), cp == null ? "" : cp);
                        }
                        break;
                    }
                }
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
        }
    }

    public static void validateMainClassInfoFromAppResources(Map<String, ? super Object> params) throws ConfigException {
        boolean hasMainClass = params.containsKey(MAIN_CLASS.getID());
        boolean hasMainJar = params.containsKey(MAIN_JAR.getID());
        boolean hasMainJarClassPath = params.containsKey(CLASSPATH.getID());
        boolean hasModule = params.containsKey(MODULE.getID());

        if (hasMainClass && hasMainJar && hasMainJarClassPath || hasModule) {
            return;
        }

        extractMainClassInfoFromAppResources(params);

        if (!params.containsKey(MAIN_CLASS.getID())) {
            if (hasMainJar) {
                throw new ConfigException(
                        MessageFormat.format(I18N.getString("error.no-main-class-with-main-jar"),
                                MAIN_JAR.fetchFrom(params)),
                        MessageFormat.format(I18N.getString("error.no-main-class-with-main-jar.advice"),
                                MAIN_JAR.fetchFrom(params)));
            } else if (hasMainJarClassPath) {
                throw new ConfigException(
                        I18N.getString("error.no-main-class-with-classpath"),
                        I18N.getString("error.no-main-class-with-classpath.advice"));
            } else {
                throw new ConfigException(
                        I18N.getString("error.no-main-class"),
                        I18N.getString("error.no-main-class.advice"));
            }
        }
    }


    private static List<String> splitStringWithEscapes(String s) {
        List<String> l = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (char c : s.toCharArray()) {
            if (escaped) {
                current.append(c);
            } else if ('"' == c) {
                quoted = !quoted;
            } else if (!quoted && Character.isWhitespace(c)) {
                l.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        l.add(current.toString());
        return l;
    }

    private static List<RelativeFileSet> createAppResourcesListFromString(String s, Map<String, ? super Object> objectObjectMap) {
        List<RelativeFileSet> result = new ArrayList<>();
        for (String path : s.split("[:;]")) {
            File f = new File(path);
            if (f.getName().equals("*") || path.endsWith("/") || path.endsWith("\\")) {
                if (f.getName().equals("*")) {
                    f = f.getParentFile();
                }
                Set<File> theFiles = new HashSet<>();
                try {
                    Files.walk(f.toPath())
                            .filter(Files::isRegularFile)
                            .forEach(p -> theFiles.add(p.toFile()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                result.add(new RelativeFileSet(f, theFiles));
            } else {
                result.add(new RelativeFileSet(f.getParentFile(), Collections.singleton(f)));
            }
        }
        return result;
    }

    private static RelativeFileSet getMainJar(String moduleName, Map<String, ? super Object> params) {
        for (RelativeFileSet rfs : APP_RESOURCES_LIST.fetchFrom(params)) {
            File appResourcesRoot = rfs.getBaseDirectory();
            File mainJarFile = new File(appResourcesRoot, moduleName);

            if (mainJarFile.exists()) {
                return new RelativeFileSet(appResourcesRoot, new LinkedHashSet<>(Collections.singletonList(mainJarFile)));
            }
            else {
                List<Path> modulePath = MODULE_PATH.fetchFrom(params);
                Path modularJarPath = JLinkBundlerHelper.findPathOfModule(modulePath, moduleName);

                if (modularJarPath != null && Files.exists(modularJarPath)) {
                    return new RelativeFileSet(appResourcesRoot, new LinkedHashSet<>(Collections.singletonList(modularJarPath.toFile())));
                }
            }
        }

        throw new IllegalArgumentException(
                new ConfigException(
                        MessageFormat.format(I18N.getString("error.main-jar-does-not-exist"), moduleName),
                        I18N.getString("error.main-jar-does-not-exist.advice")));
    }

    public static List<Path> getDefaultModulePath() {
        List<Path> result = new ArrayList();
        Path jdkModulePath = Paths.get(System.getProperty("java.home"), "jmods").toAbsolutePath();

        if (jdkModulePath != null && Files.exists(jdkModulePath)) {
            result.add(jdkModulePath);
        }
        else {
            // On a developer build the JDK Home isn't where we expect it
            // relative to the jmods directory. Do some extra
            // processing to find it.
            Map<String, String> env = System.getenv();

            if (env.containsKey("JDK_HOME")) {
                jdkModulePath = Paths.get(env.get("JDK_HOME"), ".." + File.separator + "images" + File.separator + "jmods").toAbsolutePath();

                if (jdkModulePath != null && Files.exists(jdkModulePath)) {
                    result.add(jdkModulePath);
                }
            }
        }

        return result;
    }
}
