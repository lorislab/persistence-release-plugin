/*
 * Copyright 2015 Andrej_Petras.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lorislab.maven.release.util;

import java.nio.file.Path;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author Andrej_Petras
 */
public class XMLUtil {

    private XMLUtil() {
    }
    
    public static <T> void saveObject(Path path, T object) {
        
        if (path == null || object == null) {
            throw new RuntimeException("The path to file or object is null!");
        }
        
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(object.getClass());
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.marshal(object, path.toFile());
        } catch (Exception ex) {
            throw new RuntimeException("Error saving the object to path " + path.toString(), ex);
        }
    }

    public static <T> T loadObject(Path path, Class<T> clazz) {
        T result;
        if (path == null || clazz == null) {
            throw new RuntimeException("The path to file or class is null!");
        }

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            result = (T) jaxbUnmarshaller.unmarshal(path.toFile());
        } catch (Exception ex) {
            throw new RuntimeException("Error loading the xml from path " + path.toString(), ex);
        }
        return result;
    }
}
