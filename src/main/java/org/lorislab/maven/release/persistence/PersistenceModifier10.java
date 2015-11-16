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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.lorislab.maven.release.persistence.jpa10.Persistence;

/**
 * The persistence modifier for version 1.0
 * 
 * @author Andrej_Petras
 */
public class PersistenceModifier10 extends PersistenceModifier<Persistence> {
    
    /**
     * The default constructor.
     */
    public PersistenceModifier10() {
        super(Persistence.class);
    }
    
   
    /**
     * {@inheritDoc }
     */
    @Override
    public void modifier(Persistence persistence, Map<String, String> values) {
        
        List<Persistence.PersistenceUnit> units = persistence.getPersistenceUnit();
        if (units != null) {
            for (Persistence.PersistenceUnit unit : units) {
                List<Persistence.PersistenceUnit.Properties.Property> properties = unit.getProperties().getProperty();

                if (properties != null) {
                    
                    Set<Persistence.PersistenceUnit.Properties.Property> delete = new HashSet<>();                    
                    for (Persistence.PersistenceUnit.Properties.Property pro : properties) {
                        
                        String key = pro.getName();
                        if (values.containsKey(key)) {
                            
                            String value = values.get(key);
                            if (value == null || value.isEmpty()) {
                                delete.add(pro);
                            } else {
                                pro.setValue(value);
                            }
                            
                            values.remove(key);
                        }
                    }
                                                            
                    // add new properties
                    if (!values.isEmpty()) {
                        for (Entry<String, String> entry : values.entrySet()) {
                            Persistence.PersistenceUnit.Properties.Property prop = new Persistence.PersistenceUnit.Properties.Property();
                            prop.setName(entry.getKey());
                            prop.setValue(entry.getValue());
                            properties.add(prop);
                        }
                    }
                    
                    // delete properties
                    if (!delete.isEmpty()) {
                        properties.removeAll(delete);
                    }                    
                }
            }
        }
    }
}
