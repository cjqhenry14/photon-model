/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.vsphere.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Generate exhaustive list of prop names from the vim-types.xsd found in the sdk.
 * Invoke it with the path to the vim-types.xsd and a destination folder in args[0] and args[1].
 */
public final class VimPathGenerator {
    private static final String HEADER = "/*\n"
            + " * Copyright (c) 2015-%s VMware, Inc. All Rights Reserved.\n"
            + " *\n"
            + " * Licensed under the Apache License, Version 2.0 (the \"License\"); you may not\n"
            + " * use this file except in compliance with the License.  You may obtain a copy of\n"
            + " * the License at http://www.apache.org/licenses/LICENSE-2.0\n"
            + " *\n"
            + " * Unless required by applicable law or agreed to in writing, software distributed\n"
            + " * under the License is distributed on an \"AS IS\" BASIS, without warranties or\n"
            + " * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the\n"
            + " * specific language governing permissions and limitations under the License.\n"
            + " */\n";

    private final Document doc;
    private final FileWriter writer;

    /**
     * only generate path for these data objects
     */
    private final Map<String, String> roots = new HashMap<>();

    /**
     * collect enum types in advance
     */
    private final Set<String> enums = new HashSet<>();

    private final Map<String, Map<String, FieldType>> types = new HashMap<>();

    private String packageName;

    /**
     * pretty printing
     */
    private int indent;

    public static void main(String[] args) throws Exception {
        new VimPathGenerator(new File(args[0]), new File(args[1])).generate();
    }

    private VimPathGenerator(File input, File dest) throws Exception {
        if (!input.isFile()) {
            throw new RuntimeException("file does not exist");
        }

        // assume maven when guessing package name
        String anchor = "src/main/java/";
        String pkg = dest.getAbsolutePath().replace("\\", "/");
        int i = pkg.indexOf(anchor);
        if (i >= 0) {
            pkg = pkg.substring(i + anchor.length());
            packageName = pkg.replace("/", ".");
        } else {
            packageName = null;
        }

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        this.doc = db.parse(input);

        this.writer = new FileWriter(new File(dest, "VimPath.java"));

        configure();
    }

    private void configure() {
        roots.put("vm:summary", "VirtualMachineSummary");
        roots.put("vm:config", "VirtualMachineConfigInfo");
        roots.put("vm:runtime", "VirtualMachineRuntimeInfo");

        roots.put("host:summary", "HostListSummary");
        roots.put("task:info", "TaskInfo");
    }

    private static class FieldType {
        boolean primitive;
        String name;
        boolean isArray;
    }

    private void generate() throws IOException {

        print(HEADER, "" + Calendar.getInstance().get(Calendar.YEAR));

        if (packageName != null) {
            print("package %s;\n", packageName);
        }

        print("/** This class is generated, do not edit. */");
        print("public class VimPath {");

        NodeList nodeList = doc.getElementsByTagName("simpleType");
        for (int i = 0; i < nodeList.getLength(); i++) {
            this.enums.add(((Element) nodeList.item(i)).getAttribute("name"));
        }

        nodeList = doc.getDocumentElement().getElementsByTagName("complexType");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element e = (Element) nodeList.item(i);

            if (!e.getTagName().equals("complexType")) {
                continue;
            }

            if (e.getAttribute("name").startsWith("ArrayOf")) {
                continue;
            }

            String className = e.getAttribute("name");

            Element complexContent = (Element) e.getElementsByTagName("complexContent").item(0);
            Element extension = (Element) complexContent.getElementsByTagName("extension").item(0);
            Element sequence = (Element) extension.getElementsByTagName("sequence").item(0);

            Map<String, FieldType> fields = extractFields(sequence);
            types.put(className, fields);
        }

        indent = 4;
        for (Map.Entry<String, String> e : roots.entrySet()) {
            String name = e.getKey();
            int i = name.indexOf(':');

            String prefix = name.substring(0, i) + "_";
            name = name.substring(i + 1);

            Map<String, FieldType> fields = types.get(e.getValue());
            emitConstant(prefix, name, e.getValue(), false, fields);
        }

        indent = 0;
        print("}");

        writer.close();
    }

    private void emitConstant(String prefix, String dottedPath, String type, boolean isArray,
            Map<String, FieldType> fields)
            throws IOException {
        javadoc(type, isArray);
        print("public static final String %s = %s;\n", prefix + normalize(dottedPath),
                quote(dottedPath));

        if (fields == null) {
            return;
        }
        for (Map.Entry<String, FieldType> e : fields.entrySet()) {
            String fieldName = e.getKey();
            FieldType fieldType = e.getValue();

            if (fieldType.primitive || fieldType.isArray) {
                javadoc(fieldType.name, fieldType.isArray);
                print("public static final String %s = %s;\n",
                        prefix + normalize(dottedPath) + "_" + fieldName,
                        quote(dottedPath + "." + fieldName));
            } else {
                emitConstant(prefix, dottedPath + "." + fieldName, fieldType.name, false,
                        types.get(fieldType.name));
            }
        }
    }

    private String normalize(String dottedPath) {
        return dottedPath.replace(".", "_");
    }

    private void javadoc(String propType, boolean isArray) throws IOException {
        if (isArray) {
            propType = "ArrayOf" + propType;
        }

        print("/** PropertyType: <code>%s</code> */", propType);
    }

    private boolean isPrimitive(String value) {
        return !Character.isUpperCase(value.charAt(0)) || value.equals("ManagedObjectReference") ||
                this.enums.contains(value);
    }

    private String quote(String key) {
        return "\"" + key + "\"";
    }

    private Map<String, FieldType> extractFields(Element sequence) {
        Map<String, FieldType> res = new HashMap<>();
        NodeList elements = sequence.getElementsByTagName("element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            String fieldName = e.getAttribute("name");
            String fieldType = stripNs(e.getAttribute("type"));

            FieldType type = new FieldType();
            type.primitive = isPrimitive(fieldType);
            type.isArray = isArray(e);
            type.name = fieldType;

            res.put(fieldName, type);
        }

        return res;
    }

    private boolean isArray(Element e) {
        return e.hasAttribute("maxOccurs");
    }

    private String stripNs(String superClass) {
        return superClass.substring(superClass.indexOf(':') + 1);
    }

    private void print(String fmt, Object... args) throws IOException {
        if (fmt != null) {
            for (int i = 0; i < indent; i++) {
                writer.append(" ");
                System.out.print(" ");
            }

            String line = String.format(fmt, args);
            writer.append(line);
            writer.append('\n');
            System.out.println(line);
        }
    }
}
