/*
 Copyright (C) 2004 EBI, GRL
 
 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.
 
 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.
 
 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;

/**
 * An EnsEMBL Healthcheck test case which checks all exon of a gene are on the
 * same strand and in the correct order in their transcript..
 */

public class DuplicateExons extends SingleDatabaseTestCase {

    /**
     * Create an OrphanTestCase that applies to a specific set of databases.
     */
    public DuplicateExons() {

        addToGroup("post_genebuild");
        addToGroup("release");

    }

    /**
     * Check strand order of exons.
     * 
     * @return Result.
     */

    public boolean run(DatabaseRegistryEntry dbre) {

        boolean result = true;

        String sql = "SELECT e.exon_id, e.phase, e.seq_region_start AS start, e.seq_region_end AS end, e.seq_region_id AS chromosome_id, e.end_phase, e.seq_region_strand AS strand "
                + "             FROM exon e ORDER BY chromosome_id, strand, start, end, phase, end_phase";

        Connection con = dbre.getConnection();
        try {
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            int exonStart, exonEnd, exonPhase, exonChromosome, exonId, exonEndPhase, exonStrand;
            int lastExonStart = -1;
            int lastExonEnd = -1;
            int lastExonPhase = -1;
            int lastExonChromosome = -1;
            int lastExonEndPhase = -1;
            int lastExonStrand = -1;
            int duplicateExon = 0;

            boolean first = true;

            while (rs.next()) {

                // load the vars
                exonId = rs.getInt(1);
                exonPhase = rs.getInt(2);
                exonStart = rs.getInt(3);
                exonEnd = rs.getInt(4);
                exonChromosome = rs.getInt(5);
                exonEndPhase = rs.getInt(6);
                exonStrand = rs.getInt(7);

                if (!first) {
                    if (lastExonChromosome == exonChromosome && lastExonStart == exonStart && lastExonEnd == exonEnd
                            && lastExonPhase == exonPhase && lastExonStrand == exonStrand
                            && lastExonEndPhase == exonEndPhase) {
                        duplicateExon++;
                        ReportManager.warning(this, con, "Exon " + exonId + " is duplicated.");
                    }
                } else {
                    first = false;
                    ReportManager.info(this, con, "Running duplicate exon test");
                }

                lastExonStart = exonStart;
                lastExonEnd = exonEnd;
                lastExonChromosome = exonChromosome;
                lastExonPhase = exonPhase;
                lastExonEndPhase = exonEndPhase;
                lastExonStrand = exonStrand;
            }

            if (duplicateExon > 0) {
                ReportManager.problem(this, con, duplicateExon + " duplicated Exons.");
                result = false;
            }
            rs.close();
            stmt.close();

        } catch (Exception e) {
            result = false;
            e.printStackTrace();
        }

        return result;

    }

} // ExonStrandOrder TestCase
