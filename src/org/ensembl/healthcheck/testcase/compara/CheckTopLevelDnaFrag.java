/*
 * Copyright [1999-2015] Wellcome Trust Sanger Institute and the EMBL-European Bioinformatics Institute
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


package org.ensembl.healthcheck.testcase.compara;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Map;
import java.util.Vector;

import org.ensembl.healthcheck.DatabaseRegistry;
import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Species;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.compara.AbstractComparaTestCase;
import org.ensembl.healthcheck.util.DBUtils;


/**
 * Check dnafrag table against core databases.
 */

public class CheckTopLevelDnaFrag extends AbstractComparaTestCase {

	/**
	 * Create a new instance of MetaCrossSpecies
	 */
	public CheckTopLevelDnaFrag() {
		setDescription("Check that every dnafrag corresponds to a top_level seq_region in the core DB and vice versa.");
		setTeamResponsible(Team.COMPARA);
	}

	/**
	 * Check that every dnafrag corresponds to a top_level seq_region in the core DB
	 * and vice versa.
	 * NB: A warning message is displayed if some dnafrags cannot be checked because
	 * there is not any connection to the corresponding core database.
	 * 
	 * @param dbr
	 *          The database registry containing all the specified databases.
	 * @return true if the all the dnafrags are top_level seq_regions in their corresponding
	 *    core database.
	 */
	public boolean run(DatabaseRegistryEntry comparaDbre) {

		boolean result = true;
		result &= checkTopLevelDnaFrag(comparaDbre);
		return result;
	}


	public boolean checkTopLevelDnaFrag(DatabaseRegistryEntry comparaDbre) {

		boolean result = true;
		Connection comparaCon = comparaDbre.getConnection();

		// Get list of species in compara
		Vector<Species> comparaSpecies = new Vector<Species>();
		String sql = "SELECT DISTINCT genome_db.name FROM genome_db WHERE first_release IS NOT NULL AND last_release IS NULL"
			+ " AND name <> 'ancestral_sequences'";
		try {
			Statement stmt = comparaCon.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				comparaSpecies.add(Species.resolveAlias(rs.getString(1).toLowerCase().replace(' ', '_')));
			}
			rs.close();
			stmt.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		Map<Species, DatabaseRegistryEntry> speciesMap = getSpeciesCoreDbMap(DBUtils.getMainDatabaseRegistry());

		String speciesNotFound = "";
		for (Species species : comparaSpecies) {

			if (speciesMap.containsKey(species)) {
				Connection speciesCon = speciesMap.get(species).getConnection();
				int maxRows = 50000;
				int rows = DBUtils.getRowCount(comparaCon, "SELECT COUNT(*) FROM" +
						" dnafrag LEFT JOIN genome_db USING (genome_db_id)" +
						" WHERE genome_db.name = \"" + species + "\" AND first_release IS NOT NULL AND last_release IS NULL");
				if (rows > maxRows) {
					// Divide and conquer approach for large sets
					for (int rowCount=0; rowCount<rows; rowCount+=maxRows) {
						String sql1 = "SELECT dnafrag.coord_system_name, dnafrag.name, CONCAT('length=', dnafrag.length), CONCAT('is_ref=', dnafrag.is_reference)" +
							" FROM dnafrag LEFT JOIN genome_db USING (genome_db_id)" +
							" WHERE genome_db.name = \"" + species + "\" AND first_release IS NOT NULL AND last_release IS NULL" +
							" ORDER BY (dnafrag.name)" +
							" LIMIT " + rowCount + ", " + maxRows;
						String sql2 = "SELECT coord_system.name, seq_region.name, CONCAT('length=', seq_region.length),"+
							" CONCAT('is_ref=', IF(non_ref_seq_region.seq_region_id is not null, 0, 1))" +
							" FROM seq_region" +
							" JOIN coord_system USING (coord_system_id)" +
							" JOIN seq_region_attrib USING (seq_region_id)" +
							" JOIN attrib_type USING (attrib_type_id)" +
							" LEFT JOIN (SELECT seq_region_id FROM seq_region_attrib JOIN attrib_type USING (attrib_type_id) WHERE attrib_type.code = 'non_ref') non_ref_seq_region USING (seq_region_id)" +
							" WHERE attrib_type.code = 'toplevel'" +
							" ORDER BY (seq_region.name)" +
							" LIMIT " + rowCount + ", " + maxRows;
						result &= compareQueries(comparaCon, sql1, speciesCon, sql2);
					}
				} else {
					String sql1 = "SELECT dnafrag.coord_system_name, dnafrag.name, CONCAT('length=', dnafrag.length), CONCAT('is_ref=', dnafrag.is_reference)" +
						" FROM dnafrag LEFT JOIN genome_db USING (genome_db_id)" +
						" WHERE genome_db.name = \"" + species + "\" AND first_release IS NOT NULL AND last_release IS NULL";
					String sql2 = "SELECT coord_system.name, seq_region.name, CONCAT('length=', seq_region.length),"+
						" CONCAT('is_ref=', IF(non_ref_seq_region.seq_region_id is not null, 0, 1))" +
						" FROM seq_region" +
						" JOIN coord_system USING (coord_system_id)" +
						" JOIN seq_region_attrib USING (seq_region_id)" +
						" JOIN attrib_type USING (attrib_type_id)" +
						" LEFT JOIN (SELECT seq_region_id FROM seq_region_attrib JOIN attrib_type USING (attrib_type_id) WHERE attrib_type.code = 'non_ref') non_ref_seq_region USING (seq_region_id)" +
						" WHERE attrib_type.code = 'toplevel'";
					result &= compareQueries(comparaCon, sql1, speciesCon, sql2);
				}
			} else {
				// This will trigger the warning about missing species
				if (speciesNotFound == "") {
					speciesNotFound = "" + species;
				} else {
					speciesNotFound += ", " + species;
				}
			}
		}

		// Warning about missing species
		if (speciesNotFound != "") {
			ReportManager.problem(this, comparaCon, "No connection for " + speciesNotFound);
		}

		return result;
	}

} // CheckTopLevelDnaFrag
