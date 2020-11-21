/*
 * Copyright (C) 2020 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.core.traversal.planner;

import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPVariable;
import grakn.core.common.exception.GraknException;
import grakn.core.common.parameters.Label;
import grakn.core.graph.SchemaGraph;
import grakn.core.traversal.Identifier;
import grakn.core.traversal.graph.TraversalVertex;

import static grakn.common.util.Objects.className;
import static grakn.core.common.exception.ErrorMessage.Internal.ILLEGAL_CAST;
import static graql.lang.common.GraqlToken.Predicate.Equality.EQ;

public abstract class PlannerVertex<PROPERTIES extends TraversalVertex.Properties>
        extends TraversalVertex<PlannerEdge.Directional, PROPERTIES> {

    private final Planner planner;
    private final String varPrefix = "vertex::var::" + identifier() + "::";
    private final String conPrefix = "vertex::con::" + identifier() + "::";
    private int valueIsStartingVertex;
    private int valueIsEndingVertex;
    private int valueHasIncomingEdges;
    private int valueHasOutgoingEdges;
    private boolean isInitialisedVariables;
    private boolean isInitialisedConstraints;
    MPVariable varIsStartingVertex;
    MPVariable varIsEndingVertex;
    MPVariable varHasIncomingEdges;
    MPVariable varHasOutgoingEdges;
    MPVariable varUnselectedIncomingEdges;
    MPVariable varUnselectedOutgoingEdges;
    boolean hasIndex;

    PlannerVertex(Identifier identifier, Planner planner) {
        super(identifier);
        this.planner = planner;
        this.hasIndex = false;
        isInitialisedVariables = false;
        isInitialisedConstraints = false;
    }

    abstract void updateObjective(SchemaGraph graph);

    public boolean isStartingVertex() {
        return valueIsStartingVertex == 1;
    }

    public boolean isEndingVertex() {
        return valueIsEndingVertex == 1;
    }

    public boolean hasIncomingEdges() {
        return valueHasIncomingEdges == 1;
    }

    public boolean hasOutgoingEdges() {
        return valueHasOutgoingEdges == 1;
    }

    public boolean isInitialisedVariables() {
        return isInitialisedVariables;
    }

    public boolean isInitialisedConstraints() {
        return isInitialisedConstraints;
    }

    void out(PlannerEdge edge) {
        assert edge.forward().from().equals(this);
        assert edge.backward().to().equals(this);
        out(edge.forward());
        in(edge.backward());
    }

    void in(PlannerEdge edge) {
        assert edge.forward().to().equals(this);
        assert edge.backward().from().equals(this);
        in(edge.forward());
        out(edge.backward());
    }

    Planner planner() {
        return planner;
    }

    boolean hasIndex() {
        return hasIndex;
    }

    void initialiseVariables() {
        if (hasIndex) varIsStartingVertex = planner.solver().makeIntVar(0, 1, varPrefix + "is_starting_vertex");
        varIsEndingVertex = planner.solver().makeIntVar(0, 1, varPrefix + "is_ending_vertex");
        varHasIncomingEdges = planner.solver().makeIntVar(0, 1, varPrefix + "has_incoming_edges");
        varHasOutgoingEdges = planner.solver().makeIntVar(0, 1, varPrefix + "has_outgoing_edges");

        isInitialisedVariables = true;
    }

    void initialiseConstraints() {
        assert ins().stream().allMatch(PlannerEdge.Directional::isInitialisedVariables);
        assert outs().stream().allMatch(PlannerEdge.Directional::isInitialisedVariables);
        initialiseConstraintsForIncomingEdges();
        initialiseConstraintsForOutGoingEdges();
        initialiseConstraintsForVertexFlow();
        isInitialisedConstraints = true;
    }

    void initialiseConstraintsForIncomingEdges() {
        varUnselectedIncomingEdges = planner.solver().makeIntVar(0, ins().size(), varPrefix + "unselected_incoming_edges");
        MPConstraint conUnSelectedIncomingEdges = planner.solver().makeConstraint(ins().size(), ins().size(), conPrefix + "unselected_incoming_edges");
        conUnSelectedIncomingEdges.setCoefficient(varUnselectedIncomingEdges, 1);
        ins().forEach(edge -> conUnSelectedIncomingEdges.setCoefficient(edge.varIsSelected, 1));
        MPConstraint conHasIncomingEdges = planner.solver().makeConstraint(1, ins().size(), conPrefix + "has_incoming_edges");
        conHasIncomingEdges.setCoefficient(varUnselectedIncomingEdges, 1);
        conHasIncomingEdges.setCoefficient(varHasIncomingEdges, 1);
    }

    void initialiseConstraintsForOutGoingEdges() {
        varUnselectedOutgoingEdges = planner.solver().makeIntVar(0, outs().size(), varPrefix + "unselected_outgoing_edges");
        MPConstraint conUnselectedOutgoingEdges = planner.solver().makeConstraint(outs().size(), outs().size(), conPrefix + "unselected_outgoing_edges");
        conUnselectedOutgoingEdges.setCoefficient(varUnselectedOutgoingEdges, 1);
        outs().forEach(edge -> conUnselectedOutgoingEdges.setCoefficient(edge.varIsSelected, 1));
        MPConstraint conHasOutgoingEdges = planner.solver().makeConstraint(1, outs().size(), conPrefix + "has_outgoing_edges");
        conHasOutgoingEdges.setCoefficient(varUnselectedOutgoingEdges, 1);
        conHasOutgoingEdges.setCoefficient(varHasOutgoingEdges, 1);
    }

    void initialiseConstraintsForVertexFlow() {
        MPConstraint conStartOrIncoming = planner.solver().makeConstraint(1, 1, conPrefix + "starting_or_incoming");
        if (hasIndex) conStartOrIncoming.setCoefficient(varIsStartingVertex, 1);
        conStartOrIncoming.setCoefficient(varHasIncomingEdges, 1);

        MPConstraint conEndingOrOutgoing = planner.solver().makeConstraint(1, 1, conPrefix + "ending_or_outgoing");
        conEndingOrOutgoing.setCoefficient(varIsEndingVertex, 1);
        conEndingOrOutgoing.setCoefficient(varHasOutgoingEdges, 1);

        MPConstraint conVertexFlow = planner.solver().makeConstraint(0, 0, conPrefix + "vertex_flow");
        if (hasIndex) conVertexFlow.setCoefficient(varIsStartingVertex, 1);
        conVertexFlow.setCoefficient(varHasIncomingEdges, 1);
        conVertexFlow.setCoefficient(varIsEndingVertex, -1);
        conVertexFlow.setCoefficient(varHasOutgoingEdges, -1);
    }

    void recordValues() {
        valueIsStartingVertex = hasIndex ? (int) Math.round(varIsStartingVertex.solutionValue()) : 0;
        valueIsEndingVertex = (int) Math.round(varIsEndingVertex.solutionValue());
        valueHasIncomingEdges = (int) Math.round(varHasIncomingEdges.solutionValue());
        valueHasOutgoingEdges = (int) Math.round(varHasOutgoingEdges.solutionValue());
    }

    public PlannerVertex.Thing asThing() {
        throw GraknException.of(ILLEGAL_CAST.message(className(this.getClass()), className(Thing.class)));
    }

    public PlannerVertex.Type asType() {
        throw GraknException.of(ILLEGAL_CAST.message(className(this.getClass()), className(Type.class)));
    }

    public static class Thing extends PlannerVertex<Properties.Thing> {

        Thing(Identifier identifier, Planner planner) {
            super(identifier, planner);
        }

        @Override
        protected Properties.Thing newProperties() {
            return new Properties.Thing();
        }

        @Override
        void updateObjective(SchemaGraph graph) {
            if (properties().hasIID()) {
                planner().objective().setCoefficient(varIsStartingVertex, 1);
            } else if (!properties().types().isEmpty()) {
                if (!properties().predicates().isEmpty() && properties().predicates().stream().anyMatch(p -> p.equals(EQ))) {
                    planner().objective().setCoefficient(varIsStartingVertex, properties().types().size());
                } else {
                    long count = 0;
                    for (Label label : properties().types()) {
                        count += graph.getType(label.name(), label.scope().orElse(null)).instanceCount();
                    }
                    planner().objective().setCoefficient(varIsStartingVertex, count);
                }
            }

        }

        @Override
        public boolean isThing() { return true; }

        @Override
        public PlannerVertex.Thing asThing() { return this; }

        @Override
        public void properties(TraversalVertex.Properties.Thing properties) {
            if (properties.hasIID() || !properties.types().isEmpty()) hasIndex = true;
            super.properties(properties);
        }
    }

    public static class Type extends PlannerVertex<Properties.Type> {

        Type(Identifier identifier, Planner planner) {
            super(identifier, planner);
            this.hasIndex = true; // VertexProperty.Type is always indexed
        }

        @Override
        void updateObjective(SchemaGraph graph) {
            if (properties().label().isPresent()) {
                planner().objective().setCoefficient(varIsStartingVertex, 1);
            } else if (properties().isAbstract()) {
                planner().objective().setCoefficient(varIsStartingVertex, graph.typeCount());
            } else if (properties().valueType().isPresent() || properties().regex().isPresent()) {
                planner().objective().setCoefficient(varIsStartingVertex, graph.attributeTypeCount());
            }
        }

        @Override
        protected Properties.Type newProperties() {
            return new Properties.Type();
        }

        @Override
        public boolean isType() { return true; }

        @Override
        public PlannerVertex.Type asType() { return this; }
    }
}
