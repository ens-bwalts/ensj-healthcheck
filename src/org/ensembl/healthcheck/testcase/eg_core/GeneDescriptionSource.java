/**
 * File: GeneDescriptionSourceTest.java
 * Created by: dstaines
 * Created on: May 26, 2009
 * CVS:  $$
 */
package org.ensembl.healthcheck.testcase.eg_core;

import java.util.regex.Pattern;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.ReportManager;

/**
 * Test to see if [Source:] tag is set in the description
 * 
 * @author dstaines
 * 
 */
public class GeneDescriptionSource extends AbstractEgCoreTestCase {

	private final static String QUERY = "select description from gene where description like '%[Source%'";

	private final static Pattern DES_P = Pattern
			.compile("[Source:[^;]+];Acc:[^]]+\\]");

	protected boolean runTest(DatabaseRegistryEntry dbre) {
		boolean passes = true;
		int n = 0;
		for (String des : getTemplate(dbre).queryForDefaultObjectList(QUERY,
				String.class)) {
			n++;
			if (DES_P.matcher(des).matches()) {
				passes = false;
				ReportManager
						.problem(
								this,
								dbre.getConnection(),
								"Description "
										+ des
										+ " does not contain the expected source string");
			}
		}
		if (n == 0) {
			ReportManager.problem(this, dbre.getConnection(),
					"Warning: No descripitions found with source lines");
		}
		return passes;
	}

}