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

package grakn.core.query.executor;

import grabl.tracing.client.GrablTracingThreadStatic.ThreadTrace;
import grakn.core.common.parameters.Context;
import grakn.core.concept.answer.ConceptMap;
import grakn.core.graph.Graphs;
import grakn.core.query.pattern.Disjunction;

import java.util.stream.Stream;

import static grabl.tracing.client.GrablTracingThreadStatic.traceOnThread;

public class Matcher {

    private static final String TRACE_PREFIX = "matcher.";
    private final Graphs graphMgr;
    private final Disjunction disjunction;
    private final Context.Query context;

    private Matcher(final Graphs graphMgr, final Disjunction disjunction, final Context.Query context) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "constructor")) {
            this.graphMgr = graphMgr;
            this.disjunction = disjunction;
            this.context = context;
        }
    }

    public static Matcher create(final Graphs graphMgr,
                                 final graql.lang.pattern.Conjunction<? extends graql.lang.pattern.Pattern> conjunction,
                                 final Context.Query context) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "create")) {
            return new Matcher(graphMgr, Disjunction.create(conjunction.normalise()), context);
        }
    }

    public Stream<ConceptMap> execute() {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "execute")) {
            return Stream.empty(); // TODO
        }
    }
}
