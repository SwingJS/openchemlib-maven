/*
 * Copyright (c) 2020.
 * Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91, CH-4123 Allschwil, Switzerland
 *
 *  This file is part of DataWarrior.
 *
 *  DataWarrior is free software: you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation, either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  DataWarrior is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with DataWarrior.
 *  If not, see http://www.gnu.org/licenses/.
 *
 *  @author Modest v. Korff
 *
 */

package com.actelion.research.chem.descriptor.flexophore;

import com.actelion.research.calc.ArrayUtilsCalc;
import com.actelion.research.chem.*;
import com.actelion.research.chem.descriptor.DescriptorEncoder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

public class MolDistHistVizEncoder {
	
	public static final String SEP = "-1";
	
	
	private static MolDistHistVizEncoder INSTANCE;
	
	private MolDistHistEncoder molDistHistEncoder;

	/**
	 * 
	 */
	public MolDistHistVizEncoder() {
		molDistHistEncoder = new MolDistHistEncoder();
	}
	
	/**
	 * 
	 * @param vizinfo String with content generated by toStringVizInfoEncoded(). 
	 * @param flexophore the encoded descriptor
	 * @return
	 */
	public MolDistHistViz readEncoded(String vizinfo, String flexophore){
		
		StringTokenizer st = new StringTokenizer(vizinfo);
		
		String idcode = st.nextToken();
		String coord = st.nextToken();
				
		MolDistHist mdh = molDistHistEncoder.decode(flexophore);
		
		IDCodeParser parser = new IDCodeParser();
		
		StereoMolecule ster = parser.getCompactMolecule(idcode, coord);
		ster.ensureHelperArrays(StereoMolecule.cHelperRings);
		
		int cc=0;
		List<PPNodeViz> liPPNodeViz = new ArrayList<PPNodeViz>();
		
		boolean hasInevitablePPPoints = false;
		while(st.hasMoreTokens()){
			
			String sArr = st.nextToken();
			
			if(sArr.equals(SEP)){
				hasInevitablePPPoints = true;
				break;
			}
			
			int [] arrAtIndex = ArrayUtilsCalc.readIntArray(sArr);
			
			PPNodeViz node = new PPNodeViz(mdh.getNode(cc));
			
			Coordinates [] arrCoordinates = new Coordinates [arrAtIndex.length];
			for (int i = 0; i < arrAtIndex.length; i++) {
				
				double x = ster.getAtomX(arrAtIndex[i]);
				double y = ster.getAtomY(arrAtIndex[i]);
				double z = ster.getAtomZ(arrAtIndex[i]);
				arrCoordinates[i] = new Coordinates(x, y, z);
				node.addIndexOriginalAtom(arrAtIndex[i]);
			}

			Coordinates coordCenter = Coordinates.createBarycenter(arrCoordinates);
			node.setCoordinates(coordCenter.x, coordCenter.y, coordCenter.z);
			
			liPPNodeViz.add(node);
			cc++;
		}
		
		Molecule3D ff = new Molecule3D(ster);
		for (int i = 0; i < ff.getAllAtoms(); i++) {
			
			Coordinates c = new Coordinates(ster.getAtomX(i), ster.getAtomY(i), ster.getAtomZ(i));
			
			ff.setCoordinates(i, c);
		}
		
		MolDistHistViz mdhv = new MolDistHistViz(mdh.getNumPPNodes(), ff);
		for (int i = 0; i < mdh.getNumPPNodes(); i++) {
			mdhv.addNode(liPPNodeViz.get(i));
		}
		
		for (int i = 0; i < mdh.getNumPPNodes(); i++) {
			for (int j = i+1; j < mdh.getNumPPNodes(); j++) {
				mdhv.setDistHist(i, j, mdh.getDistHist(i, j));
			}
		}
		
		if(hasInevitablePPPoints) {
			
			String strInevitablePPPoints = st.nextToken();
			
			DescriptorEncoder de = new DescriptorEncoder();

			byte [] arrIndexInevitablePPPoints = de.decodeCounts(strInevitablePPPoints);
			
			for (int i = 0; i < arrIndexInevitablePPPoints.length; i++) {
				mdhv.addInevitablePharmacophorePoint(arrIndexInevitablePPPoints[i]);
			}
			
		}
		
		mdhv.realize();
		
		return mdhv;
		
	}
	
	/**
	 * Encodes the structure information, that it can be written to an DWAR file. 
	 * The atom indices from the structure are mapped on the nodes 
	 * under consideration of the changed indices generated by the Canonizer.
	 * H atoms are not covered by the indices generated by the Canonizer. We take simply the old indices and hope the best.
	 * @return A String with: idcode coordinates [int array]n for each PPNode an int array is written, 
	 * it contains the indices of the mapping atoms in the idcode.  
	 */
	public static String toStringVizInfoEncoded(MolDistHistViz mdhv) {

		StringBuilder sb = new StringBuilder();
		
		if(mdhv.molecule3D == null)
			return null;
		
		Molecule3D molecule3D = MolDistHistViz.finalizeMolecule(mdhv.molecule3D);

		List<List<Integer>> liliOriginalIndex = new ArrayList<List<Integer>>();
		for(int i=0; i < mdhv.getNumPPNodes(); i++){
			PPNodeViz node = mdhv.getNode(i);
			List<Integer> liOriginalIndex = node.getListIndexOriginalAtoms();
			liliOriginalIndex.add(liOriginalIndex);
		}
		
		StereoMolecule mol = new Molecule3D(molecule3D);
		
		mol.ensureHelperArrays(Molecule.cHelperRings);
		

		Canonizer can = new Canonizer(mol);
		
		String idcode = can.getIDCode();
		
		String coord = can.getEncodedCoordinates(true);
		
		int [] arrMap = can.getGraphIndexes();

		int nNonHAtoms = mol.getAtoms();
		
		for (int i = 0; i < liliOriginalIndex.size(); i++) {
			List<Integer> liOriginalIndex = liliOriginalIndex.get(i);
			for (int j = 0; j < liOriginalIndex.size(); j++) {
				int ind = liOriginalIndex.get(j);
				try {
					if(ind<nNonHAtoms) {
						liOriginalIndex.set(j, arrMap[ind]);
					} else { // For the H atoms we hope the best.
						liOriginalIndex.set(j, ind);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		sb.append(idcode);
		sb.append(" ");
		sb.append(coord);
		sb.append(" ");
		for (int i = 0; i < liliOriginalIndex.size(); i++) {
			sb.append(ArrayUtilsCalc.toString(liliOriginalIndex.get(i)));
			if(i<liliOriginalIndex.size()-1)
				sb.append(" ");
		}
		
		HashSet<Integer> hsIndexInevitablePPPoints = mdhv.getHashSetIndexInevitablePPPoints();
		
		if(hsIndexInevitablePPPoints.size() > 0)  {
			
			sb.append(" " + SEP + " ");
			
			DescriptorEncoder de = new DescriptorEncoder();
			
			byte [] a = new byte [hsIndexInevitablePPPoints.size()];
			
			int cc=0;
			for (int index : hsIndexInevitablePPPoints) {
				a[cc++]=(byte)index;
			}
			
			String strEncodedInevitablePPPoints = new String(de.encodeCounts(a));
			
			sb.append(strEncodedInevitablePPPoints);
		}
		
		return sb.toString();
	}
	
	public static MolDistHistVizEncoder getInstance(){
		
		if(INSTANCE == null){
			INSTANCE = new MolDistHistVizEncoder();
		}
		
		return INSTANCE;
	}


}
