/*
 * This file is part of the OWL API.
 *
 * The contents of this file are subject to the LGPL License, Version 3.0.
 *
 * Copyright (C) 2011, The University of Manchester
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0
 * in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 *
 * Copyright 2011, The University of Manchester
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.semanticweb.owlapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataAllValuesFrom;
import org.semanticweb.owlapi.model.OWLDataExactCardinality;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataHasValue;
import org.semanticweb.owlapi.model.OWLDataMaxCardinality;
import org.semanticweb.owlapi.model.OWLDataMinCardinality;
import org.semanticweb.owlapi.model.OWLDataOneOf;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDataSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNegativeDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.util.OWLObjectDuplicator;

/**
 * Coerces constants to have the same type as the range of a property in axioms
 * where the two are used. For example, given, p value "xyz", the "xyz" constant
 * would be typed with the range of p.
 * 
 * @author Matthew Horridge, The University Of Manchester, Bio-Health
 *         Informatics Group, Date: 13-Aug-2007
 */
public class CoerceConstantsIntoDataPropertyRange extends
        AbstractCompositeOntologyChange {

    protected final Map<OWLDataPropertyExpression, OWLDatatype> map;
    private final List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();

    /**
     * @param ontologies
     *        the ontologies to use
     * @param dataFactory
     *        the data factory
     */
    public CoerceConstantsIntoDataPropertyRange(OWLDataFactory dataFactory,
            Set<OWLOntology> ontologies) {
        super(dataFactory);
        map = new HashMap<OWLDataPropertyExpression, OWLDatatype>();
        for (OWLOntology ont : ontologies) {
            for (OWLDataPropertyRangeAxiom ax : ont
                    .getAxioms(AxiomType.DATA_PROPERTY_RANGE)) {
                if (ax.getRange().isDatatype()) {
                    map.put(ax.getProperty(), (OWLDatatype) ax.getRange());
                }
            }
        }
        OWLConstantReplacer replacer = new OWLConstantReplacer(getDataFactory());
        for (OWLOntology ont : ontologies) {
            for (OWLAxiom ax : ont.getLogicalAxioms()) {
                OWLAxiom dupAx = replacer.duplicateObject(ax);
                if (!ax.equals(dupAx)) {
                    changes.add(new RemoveAxiom(ont, ax));
                    changes.add(new AddAxiom(ont, dupAx));
                }
            }
        }
    }

    @Override
    public List<OWLOntologyChange> getChanges() {
        return changes;
    }

    private class OWLConstantReplacer extends OWLObjectDuplicator {

        public OWLConstantReplacer(OWLDataFactory dataFactory) {
            super(dataFactory);
        }

        private OWLDataOneOf process(OWLDataPropertyExpression prop,
                OWLDataOneOf oneOf) {
            Set<OWLLiteral> vals = new HashSet<OWLLiteral>();
            for (OWLLiteral con : oneOf.getValues()) {
                vals.add(process(prop, con));
            }
            return getDataFactory().getOWLDataOneOf(vals);
        }

        private OWLLiteral process(OWLDataPropertyExpression prop,
                OWLLiteral con) {
            OWLDatatype dt = map.get(prop);
            if (dt != null) {
                return getDataFactory().getOWLLiteral(con.getLiteral(), dt);
            } else {
                return con;
            }
        }

        @Override
        public void visit(OWLDataHasValue desc) {
            super.visit(desc);
            setLastObject(getDataFactory().getOWLDataHasValue(
                    desc.getProperty(),
                    process(desc.getProperty(), desc.getValue())));
        }

        @Override
        public void visit(OWLDataSomeValuesFrom desc) {
            super.visit(desc);
            if (desc instanceof OWLDataOneOf) {
                setLastObject(getDataFactory().getOWLDataSomeValuesFrom(
                        desc.getProperty(),
                        process(desc.getProperty(),
                                (OWLDataOneOf) desc.getFiller())));
            }
        }

        @Override
        public void visit(OWLDataMinCardinality desc) {
            super.visit(desc);
            if (desc instanceof OWLDataOneOf) {
                setLastObject(getDataFactory().getOWLDataMinCardinality(
                        desc.getCardinality(),
                        desc.getProperty(),
                        process(desc.getProperty(),
                                (OWLDataOneOf) desc.getFiller())));
            }
        }

        @Override
        public void visit(OWLDataMaxCardinality desc) {
            super.visit(desc);
            if (desc instanceof OWLDataOneOf) {
                setLastObject(getDataFactory().getOWLDataMaxCardinality(
                        desc.getCardinality(),
                        desc.getProperty(),
                        process(desc.getProperty(),
                                (OWLDataOneOf) desc.getFiller())));
            }
        }

        @Override
        public void visit(OWLDataExactCardinality desc) {
            super.visit(desc);
            if (desc instanceof OWLDataOneOf) {
                setLastObject(getDataFactory().getOWLDataExactCardinality(
                        desc.getCardinality(),
                        desc.getProperty(),
                        process(desc.getProperty(),
                                (OWLDataOneOf) desc.getFiller())));
            }
        }

        @Override
        public void visit(OWLDataAllValuesFrom desc) {
            super.visit(desc);
            if (desc instanceof OWLDataOneOf) {
                setLastObject(getDataFactory().getOWLDataAllValuesFrom(
                        desc.getProperty(),
                        process(desc.getProperty(),
                                (OWLDataOneOf) desc.getFiller())));
            }
        }

        @Override
        public void visit(OWLDataPropertyAssertionAxiom axiom) {
            super.visit(axiom);
            setLastObject(getDataFactory().getOWLDataPropertyAssertionAxiom(
                    axiom.getProperty(), axiom.getSubject(),
                    process(axiom.getProperty(), axiom.getObject())));
        }

        @Override
        public void visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
            super.visit(axiom);
            setLastObject(getDataFactory()
                    .getOWLNegativeDataPropertyAssertionAxiom(
                            axiom.getProperty(), axiom.getSubject(),
                            process(axiom.getProperty(), axiom.getObject())));
        }
    }
}
