package ch.unibas.cs.dbis.cineast.core.features;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import ch.unibas.cs.dbis.cineast.core.config.Config;
import ch.unibas.cs.dbis.cineast.core.data.FloatVector;
import ch.unibas.cs.dbis.cineast.core.data.FloatVectorImpl;
import ch.unibas.cs.dbis.cineast.core.data.FrameContainer;
import ch.unibas.cs.dbis.cineast.core.data.LongDoublePair;
import ch.unibas.cs.dbis.cineast.core.data.Pair;
import ch.unibas.cs.dbis.cineast.core.features.abstracts.SubDivMotionHistogram;
import ch.unibas.cs.dbis.cineast.core.util.MathHelper;

public class SubDivMotionHistogram2 extends SubDivMotionHistogram {

	public SubDivMotionHistogram2() {
		super("features.SubDivMotionHistogram2", "hists", MathHelper.SQRT2 * 4);
	}

	@Override
	public void processShot(FrameContainer shot) {
		if(!phandler.check("SELECT * FROM features.SubDivMotionHistogram2 WHERE shotid = " + shot.getId())){
			
			Pair<List<Double>, ArrayList<ArrayList<Float>>> pair = getSubDivHist(2, shot.getPaths());
			
			FloatVector sum = new FloatVectorImpl(pair.first);
			ArrayList<Float> tmp = new ArrayList<Float>(2 * 2 * 8);
			for(List<Float> l : pair.second){
				for(float f : l){
					tmp.add(f);
				}
			}
			FloatVectorImpl fv = new FloatVectorImpl(tmp);

			addToDB(shot.getId(), sum, fv);
		}
	}

	@Override
	public List<LongDoublePair> getSimilar(FrameContainer qc) {
		int limit = Config.getRetrieverConfig().getMaxResultsPerModule();
		
		Pair<List<Double>, ArrayList<ArrayList<Float>>> pair = getSubDivHist(2, qc.getPaths());

		ArrayList<Float> tmp = new ArrayList<Float>(2 * 2 * 8);
		for(List<Float> l : pair.second){
			for(float f : l){
				tmp.add(f);
			}
		}
		FloatVectorImpl fv = new FloatVectorImpl(tmp);
		
		ResultSet rset = this.selector.select("SELECT * FROM features.SubDivMotionHistogram2 USING DISTANCE MINKOWSKI(2)(\'" + fv.toFeatureString() + "\', hists) ORDER USING DISTANCE LIMIT " + limit);
		return manageResultSet(rset);
	}

	@Override
	public List<LongDoublePair> getSimilar(FrameContainer qc, String resultCacheName) {
		int limit = Config.getRetrieverConfig().getMaxResultsPerModule();
		
		Pair<List<Double>, ArrayList<ArrayList<Float>>> pair = getSubDivHist(2, qc.getPaths());

		ArrayList<Float> tmp = new ArrayList<Float>(2 * 2 * 8);
		for(List<Float> l : pair.second){
			for(float f : l){
				tmp.add(f);
			}
		}
		FloatVectorImpl fv = new FloatVectorImpl(tmp);
		
		ResultSet rset = this.selector.select(getResultCacheLimitSQL(resultCacheName) + " SELECT * FROM features.SubDivMotionHistogram2, c WHERE shotid = c.filter USING DISTANCE MINKOWSKI(2)(\'" + fv.toFeatureString() + "\', hists) ORDER USING DISTANCE LIMIT " + limit);
		return manageResultSet(rset);
	}
}
