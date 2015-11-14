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
import java.util.List;
import java.util.Map;
import org.lorislab.maven.release.persistence.jpa10.Persistence;
import org.lorislab.maven.release.util.XMLUtil;

/**
 *
 * @author Andrej_Petras
 */
public class PersistenceModifier10 implements PersistenceModifier {

    @Override
    public void modifier(Path path, Map<String, String> values) {
        Persistence persistence = XMLUtil.loadObject(path, Persistence.class);

        List<Persistence.PersistenceUnit> units = persistence.getPersistenceUnit();
        if (units != null) {
            for (Persistence.PersistenceUnit unit : units) {
                List<Persistence.PersistenceUnit.Properties.Property> properties = unit.getProperties().getProperty();

                if (properties != null) {
                    for (Persistence.PersistenceUnit.Properties.Property pro : properties) {
                        if (values.containsKey(pro.getName())) {
                            pro.setValue(values.get(pro.getName()));                            
                        }
                    }
                }
            }
        }

        XMLUtil.saveObject(path, persistence);
    }
    
}
