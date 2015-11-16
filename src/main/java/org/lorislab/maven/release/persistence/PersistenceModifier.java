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
package org.lorislab.maven.release.persistence;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.lorislab.maven.release.util.XMLUtil;

/**
 *
 * @author Andrej_Petras
 */
public abstract class PersistenceModifier<T> {
    
    private final Class<T> clazz;

    public PersistenceModifier(Class<T> clazz) {
        this.clazz = clazz;
    }
        
    public void modifier(Path path, Map<String, String> values) {
        T persistence = XMLUtil.loadObject(path, clazz);
        
        Map<String, String> tmp = new HashMap<>(values);
        modifier(persistence, tmp);
        XMLUtil.saveObject(path, persistence);        
    }
    
    protected abstract void modifier(T persistence, Map<String, String> values);
}
