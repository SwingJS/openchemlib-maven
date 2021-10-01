package com.actelion.research.chem.alignment3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.IntStream;

import com.actelion.research.calc.Matrix;
import com.actelion.research.chem.Coordinates;
import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.alignment3d.transformation.Rotation;
import com.actelion.research.chem.alignment3d.transformation.TransformationSequence;
import com.actelion.research.chem.conf.Conformer;

import com.actelion.research.chem.phesa.MolecularVolume;
import com.actelion.research.chem.phesa.PheSAAlignment;
import com.actelion.research.chem.phesa.PheSAAlignment.PheSAResult;
import com.actelion.research.chem.phesa.PheSAMolecule;
import com.actelion.research.chem.phesa.ShapeVolume;
import com.actelion.research.chem.phesa.pharmacophore.PPTriangle;
import com.actelion.research.chem.phesa.pharmacophore.PPTriangleCreator;
import com.actelion.research.chem.phesa.pharmacophore.PPTriangleMatcher;
import com.actelion.research.chem.phesa.pharmacophore.PharmacophoreCalculator;
import com.actelion.research.chem.phesa.pharmacophore.pp.PPGaussian;

public class PheSAAlignmentOptimizer {
	
	private static int TRIANGLE_OPTIMIZATIONS = 100;
	private static int PMI_OPTIMIZATIONS = 20;
	private static double EXIT_VECTOR_WEIGHT = 10.0;
	private static final int BEST_RESULT_SIZE = 20;
	
	private PheSAAlignmentOptimizer() {}
	
	
	public static double alignTwoMolsInPlace(StereoMolecule refMol, StereoMolecule fitMol) {
		return alignTwoMolsInPlace(refMol,fitMol,0.5);
	}
	
	public static double alignTwoMolsInPlace(StereoMolecule refMol, StereoMolecule fitMol, double ppWeight) {
		double similarity = 0.0;
		MolecularVolume refVol = new MolecularVolume(refMol);
		MolecularVolume fitVol = new MolecularVolume(fitMol);
		Coordinates origCOM = new Coordinates(refVol.getCOM());
		Conformer refConf = new Conformer(refMol);
		Conformer fitConf = new Conformer(fitMol);
		Rotation rotation = refVol.preProcess(refConf);
		rotation = rotation.getInvert();
		fitVol.preProcess(fitConf);
		AlignmentResult bestSolution = createAlignmentSolutions(Collections.singletonList(refVol), Collections.singletonList(fitVol),ppWeight,true,false,true).last();
		similarity = bestSolution.getSimilarity();
		
		for(int a=0;a<fitMol.getAllAtoms();a++) {
			fitMol.setAtomX(a, fitConf.getX(a));
			fitMol.setAtomY(a, fitConf.getY(a));
			fitMol.setAtomZ(a, fitConf.getZ(a));
		}
		bestSolution.getTransform().apply(fitMol);
		rotation.apply(fitMol);
		fitMol.translate(origCOM.x, origCOM.y, origCOM.z);

		return similarity;
		
		
		
	}
	
	public static List<AlignmentResult> alignToNegRecImg(ShapeVolume ref, List<? extends ShapeVolume> fitVols, double ppWeight, boolean optimize) {
		for(ShapeVolume shapeVol : fitVols) {
			shapeVol.removeRings();
		}
		NavigableSet<AlignmentResult> alignmentSolutions = createAlignmentSolutions(Collections.singletonList(ref),fitVols,ppWeight,optimize,true,false); 
		List<AlignmentResult> results = new ArrayList<>();
		int counter = 0;
		for(AlignmentResult solution : alignmentSolutions.descendingSet() ) {
			if(counter++>=BEST_RESULT_SIZE) {
				break;
			}
			results.add(solution);
		
		}
		return results;
	}
	
	public static NavigableSet<AlignmentResult> createAlignmentSolutions(List<? extends ShapeVolume> refVols, List<? extends ShapeVolume> fitVols, double ppWeight, boolean optimize,
			boolean tversky, boolean useDirectionality) {
		NavigableSet<AlignmentResult> alignmentSolutions = new TreeSet<AlignmentResult>();

		for(ShapeVolume molVol : refVols) {
			for(PPGaussian ppg : molVol.getPPGaussians()) {
				if(ppg.getPharmacophorePoint().getFunctionalityIndex()==PharmacophoreCalculator.EXIT_VECTOR_ID) {
					ppg.setWeight(EXIT_VECTOR_WEIGHT);
				}
			}
		}

		NavigableSet<AlignmentResult> triangleSolutions = getBestTriangleAlignments(refVols,fitVols,ppWeight,optimize,tversky,useDirectionality);
		alignmentSolutions.addAll(triangleSolutions);
		NavigableSet<AlignmentResult> pmiSolutions = new TreeSet<AlignmentResult>();
		for(int i=0;i<refVols.size();i++) {
			ShapeVolume refVol = refVols.get(i);
			for(int j=0;j<fitVols.size();j++) {
				ShapeVolume fitVol = new ShapeVolume(fitVols.get(j));
				PheSAAlignment shapeAlignment = new PheSAAlignment(refVol,fitVol, ppWeight);
				TransformationSequence pmiTransformation = new TransformationSequence();
				double[][] transforms = PheSAAlignment.initialTransform(2);
				//if(tversky)
				//	transforms = createSubAlignments(refVol,PheSAAlignment.initialTransform(2));
				double[] r = shapeAlignment.findAlignment(transforms,pmiTransformation,false,tversky);
				AlignmentResult pmiAlignment = new AlignmentResult(r[0],pmiTransformation,i,j);
				pmiAlignment.setSimilarityContributions(r);
				pmiSolutions.add(pmiAlignment);
			}
		}
		//optimize best PMI alignments
		int counter = 0;
		for(AlignmentResult pmiAlignment : pmiSolutions.descendingSet()) {
			if(counter++>PMI_OPTIMIZATIONS)
				break;
			ShapeVolume refVol = refVols.get(pmiAlignment.getRefConformerIndex());
			ShapeVolume fitVol = new ShapeVolume(fitVols.get(pmiAlignment.getConformerIndex()));
			TransformationSequence bestTransform = pmiAlignment.getTransform();
			fitVol.transform(bestTransform);
			PheSAAlignment shapeAlignment = new PheSAAlignment(refVol,fitVol,ppWeight);
			TransformationSequence optimizedTransform = new TransformationSequence();
			double[] r = shapeAlignment.findAlignment(PheSAAlignment.initialTransform(0),optimizedTransform,true,tversky);
			pmiAlignment.getTransform().addTransformation(optimizedTransform);
			AlignmentResult optimizedPMIAlignment = new AlignmentResult(r[0],pmiAlignment.getTransform(),pmiAlignment.getRefConformerIndex(),pmiAlignment.getConformerIndex());
			optimizedPMIAlignment.setSimilarityContributions(r);
			alignmentSolutions.add(optimizedPMIAlignment);
			
		}
		return alignmentSolutions;
		
	}
	
	public static double[] align(PheSAMolecule refShape, PheSAMolecule fitShape, StereoMolecule[] bestAlignment, double ppWeight, boolean optimize) {
		double[] result = new double[4]; //overall sim, ppSimilarity and additional volume similarity contribution
		
		NavigableSet<AlignmentResult> alignmentSolutions = createAlignmentSolutions(refShape.getVolumes(),fitShape.getVolumes(),ppWeight,optimize,false,true); 
		AlignmentResult bestResult = alignmentSolutions.last();
		StereoMolecule refMol = refShape.getConformer(bestResult.getRefConformerIndex());
		StereoMolecule fitMol = fitShape.getConformer(bestResult.getConformerIndex());
		bestResult.getTransform().apply(fitMol);
		result = bestResult.getSimilarityContributions();
		bestAlignment[0] = refMol;
		bestAlignment[1] = fitMol;
		int n1 = refShape.getVolumes().get(0).getExitVectorGaussians().size();
		int n2 = fitShape.getVolumes().get(0).getExitVectorGaussians().size();
		if(refShape.getVolumes().get(0).getExitVectorGaussians().size()!=0 || 
				refShape.getVolumes().get(0).getExitVectorGaussians().size()!=0) {
			// there are exit vectors 
			
			if(n1!=n2) { //different number of exit vectors --> no match
				result[0] = 0.0;
				result[1] = 0.0;
				result[2] = 0.0;
				result[3] = 0.0;
			}
			
		}
	
		return result;
	}
	

	
	private static NavigableSet<AlignmentResult> getBestTriangleAlignments(List<? extends ShapeVolume> refVols, List<? extends ShapeVolume> fitVols, double ppWeight, 
			boolean optimize, boolean tversky, boolean useDirectionality) {
		TreeSet<AlignmentResult> triangleResults = new TreeSet<AlignmentResult>();
		for(int i=0;i<refVols.size();i++) {
			ShapeVolume refVol = refVols.get(i);
			Map<Integer,ArrayList<PPTriangle>> refTriangles = PPTriangleCreator.create(refVol.getPPGaussians(), refVol.getCOM());
			for(int j=0;j<fitVols.size();j++) {
				ShapeVolume fitVol = fitVols.get(j);
				Map<Integer,ArrayList<PPTriangle>> fitTriangles = PPTriangleCreator.create(fitVol.getPPGaussians(),fitVol.getCOM());
				triangleResults.addAll(PPTriangleMatcher.getMatchingTransforms(refTriangles, fitTriangles,i,j,useDirectionality));
			}
		}
		NavigableSet<AlignmentResult> optimizedResults = new TreeSet<AlignmentResult>();
		if(triangleResults.size()!=0) { // found triangle alignments

			double[][] alignments = PheSAAlignment.initialTransform(0);
			int counter = 0;
			for(AlignmentResult result: triangleResults.descendingSet()) {
				if(counter++>TRIANGLE_OPTIMIZATIONS)
					break;
				ShapeVolume refVol = refVols.get(result.getRefConformerIndex());
				ShapeVolume fitVol = new ShapeVolume(fitVols.get(result.getConformerIndex()));
				TransformationSequence bestTransform = result.getTransform();
				fitVol.transform(bestTransform);
				PheSAAlignment shapeAlignment = new PheSAAlignment(refVol,fitVol,ppWeight);
				TransformationSequence optTransform = new TransformationSequence();
				double[] r = shapeAlignment.findAlignment(alignments,optTransform,optimize, tversky);
				bestTransform.addTransformation(optTransform);
				AlignmentResult optimizedResult = new AlignmentResult(r[0], bestTransform, result.getRefConformerIndex(), result.getConformerIndex());
				optimizedResult.setSimilarityContributions(r);
				optimizedResults.add(optimizedResult);
			}
		}

		return optimizedResults;
	}
	
	private static double[][] createSubAlignments(ShapeVolume refVol, double[][] baseTransforms) {
		final long seed = 12345L;
		final int maxPoints = 10;
		final int points = Math.min(refVol.getAtomicGaussians().size(), maxPoints);
		Random rnd = new Random(seed);
		List<double[]> transforms = new ArrayList<>(); 
		for(int i=0;i<points;i++) {
			int index = rnd.nextInt(refVol.getAtomicGaussians().size());
			Coordinates c = refVol.getAtomicGaussians().get(index).getCenter();
			for(double[] t : baseTransforms) {
				transforms.add(new double[] {t[0],t[1],t[2],c.x,c.y,c.z});
			}
		}
		for(double[] t : baseTransforms) {
			transforms.add(t);
		}
		return transforms.toArray(new double[0][]);
	}
	

	
	public static class AlignmentResult implements Comparable<AlignmentResult>{
		private double similarity;
		private TransformationSequence transformation;
		private int conformerIndex;
		private int refConformerIndex;
		private double[] similarityContributions;
		
		public AlignmentResult(double similarity, TransformationSequence transformation, int refConformerIndex, int conformerIndex) {
			this.similarity = similarity;
			this.transformation = transformation;
			this.refConformerIndex = refConformerIndex;
			this.conformerIndex = conformerIndex;
		}
		
		
		public double[] getSimilarityContributions() {
			return similarityContributions;
		}


		public void setSimilarityContributions(double[] similarityContributions) {
			this.similarityContributions = similarityContributions;
		}


		public TransformationSequence getTransform() {
			return transformation;
		}

		
		public double getSimilarity() {
			return similarity;
		}
		
		public int getConformerIndex() {
			return conformerIndex;
		}
		
		public int getRefConformerIndex() {
			return refConformerIndex;
		}


		@Override
		public int compareTo(AlignmentResult o) {
			return Double.compare(similarity, o.similarity);
		}
		
		
		
	}

	

}
