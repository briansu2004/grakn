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

package hypergraph;

import hypergraph.core.HypergraphCore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HypergraphTest {

    @Test
    public void test_opening_a_graph() throws IOException {
        Path directory = Paths.get(System.getProperty("user.dir")).resolve("grakn");
        Files.createDirectory(directory);

        System.out.println("Database Directory: " + directory.toString());

        try (Hypergraph graph = new HypergraphCore(directory.toString())) {
            assertTrue(graph.isOpen());

            try (Hypergraph.Session session = graph.session("my_data_keyspace")) {
                assertTrue(session.isOpen());
                assertEquals("my_data_keyspace", session.keyspace());

                try (Hypergraph.Transaction transaction = session.transaction(Hypergraph.Transaction.Type.READ)) {
                    assertTrue(transaction.isOpen());
                    assertEquals(HypergraphCore.Transaction.Type.READ, transaction.type());



                }
            }
        }
    }
}
