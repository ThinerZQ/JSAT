
package jsat.classifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import jsat.linear.DenseVector;
import jsat.linear.Vec;
import jsat.math.OnLineStatistics;

/**
 *
 * @author Edward Raff
 */
public class ClassificationDataSet //extends DataSet
{
    /**
     * The number of numerical values each data point must have
     */
    private int numNumerVals;
    
    /**
     * Contains the categories for each of the categorical variables
     */
    protected CategoricalData[] categories;
    /**
     * The categories for the predicted value
     */
    protected CategoricalData predicting;
    private int numOfSamples = 0;
    
    /**
     * Contains a list of data points that have already been classified according to {@link #predicting}
     */
    protected List<List<DataPoint>> classifiedExamples;

    /**
     * 
     * @param data 
     * @param predicting the index of the categorical variable to be the prediction target
     */
    public ClassificationDataSet(List<DataPoint> data, int predicting)
    {
        //Use the first data point to set up
        DataPoint tmp = data.get(0);
        categories = new CategoricalData[tmp.numCategoricalValues()-1];
        for(int i = 0; i < categories.length; i++)
        {
            categories[i] = i >= predicting ? 
                    tmp.getCategoricalData()[i+1] : tmp.getCategoricalData()[i];
        }
        numNumerVals = tmp.numNumericalValues();
        this.predicting = tmp.getCategoricalData()[predicting];
        
        classifiedExamples = new ArrayList<List<DataPoint>>(this.predicting.getNumOfCategories());
        for(int i = 0; i < this.predicting.getNumOfCategories(); i++)
            classifiedExamples.add(new ArrayList<DataPoint>());
        
        
        //Fill up data
        for(DataPoint dp : data)
        {
            int[] newCats = new int[dp.numCategoricalValues()-1];
            int[] prevCats = dp.getCategoricalValues();
            int k = 0;//index for the newCats 
            for(int i = 0; i < prevCats.length; i++)
            {
                if(i != predicting)
                    newCats[k++] = prevCats[i];
            }
            DataPoint newPoint = new DataPoint(dp.getNumericalValues(), newCats, categories);
            classifiedExamples.get(prevCats[predicting]).add(newPoint); 
        }
        
        numOfSamples = data.size();
    }
    
    
    public ClassificationDataSet(int numerical, CategoricalData[] categories, CategoricalData predicting)
    {
        this.predicting = predicting;
        this.categories = categories;
        this.numNumerVals = numerical;
        
        classifiedExamples = new ArrayList<List<DataPoint>>();
        for(int i = 0; i < predicting.getNumOfCategories(); i++)
            classifiedExamples.add(new ArrayList<DataPoint>());
    }
    
    public void applyTransform(DataTransform dt)
    {
        for(int i = 0; i < classifiedExamples.size(); i++)
            for(int j = 0; j < classifiedExamples.get(i).size(); j++)
                classifiedExamples.get(i).set(j, dt.transform(classifiedExamples.get(i).get(j)));
        
        numNumerVals = classifiedExamples.get(0).get(0).numNumericalValues();
        categories = classifiedExamples.get(0).get(0).getCategoricalData();
    }
    
    /**
     * 
     * @param list a list of data sets
     * @param exception the one data set in the list NOT to combine into one file
     * @return a combination of all the data sets in <tt>list</tt> except the one at index <tt>exception</tt>
     */
    public static ClassificationDataSet comineAllBut(List<ClassificationDataSet> list, int exception)
    {
        int numer = list.get(exception).getNumNumericalVars();
        CategoricalData[] categories = list.get(exception).getCategories();
        CategoricalData predicting = list.get(exception).getPredicting();
        
        ClassificationDataSet cds = new ClassificationDataSet(numer, categories, predicting);
        
        //The list of data sets
        for(int i = 0; i < list.size(); i++)
        {
            if(i == exception)
                continue;
            //each category of lists
            for(int j = 0; j < predicting.getNumOfCategories(); j++)
            {
                //each sample interface a given category
                for(DataPoint dp : list.get(i).classifiedExamples.get(j))
                    cds.addDataPoint(dp.getNumericalValues(), dp.getCategoricalValues(), j);
            }
        }
        
        return cds;
    }
    
    /**
     * 
     * @param i the i'th data point in this set
     * @return the ith data point in this set
     */
    public DataPoint getDataPoint(int i)
    {
        if(i >= getSampleSize())
            throw new IndexOutOfBoundsException("There are not that many samples in the data set");
        int set = 0;
        
        while(i >= classifiedExamples.get(set).size())
            i -= classifiedExamples.get(set++).size();
        
        return classifiedExamples.get(set).get(i);
    }
    
    public int getDataPointCategory(int i)
    {
        if(i >= getSampleSize())
            throw new IndexOutOfBoundsException("There are not that many samples in the data set");
        int set = 0;
        
        while(i >= classifiedExamples.get(set).size())
            i -= classifiedExamples.get(set++).size();
        
        return set;
    }
    
    
    public List<ClassificationDataSet> cvSet(int folds)
    {
        ArrayList<ClassificationDataSet> cvList = new ArrayList<ClassificationDataSet>();
        
        
        List<DataPoint> randSmpls = new ArrayList<DataPoint>(getSampleSize());
        List<Integer> cats = new ArrayList<Integer>(getSampleSize());
        
        for(int i = 0; i < classifiedExamples.size(); i++)
            for(DataPoint dp : classifiedExamples.get(i))
            {
                randSmpls.add(dp); 
                cats.add(i);
            }
        
        
        //Shuffle them TOGETHER, we need to keep track of the category!
        Random rnd = new Random();
        for(int i = getSampleSize()-1; i > 0; i--)
        {
            int swapPos = rnd.nextInt(i);
            //Swap DataPoint
            DataPoint tmp = randSmpls.get(i);
            randSmpls.set(i, randSmpls.get(swapPos));
            randSmpls.set(swapPos, tmp);
            
            int tmpI = cats.get(i);
            cats.set(i, cats.get(swapPos));
            cats.set(swapPos, tmpI);
        }
        
        
        //Initalize all of the new ClassificationDataSets
        for(int i = 0;  i < folds; i++)
            cvList.add(new ClassificationDataSet(getNumNumericalVars(), getCategories(), getPredicting())); 
        
        
        
        int splitSize = getSampleSize()/folds;
        
        //Keep track completly
        int k = 0;
        for(int i = 0; i < folds-1; i++)
        {
            for(int j = 0; j < splitSize; j++)
            {
                DataPoint dp = randSmpls.get(k);
                cvList.get(i).addDataPoint(dp.getNumericalValues(), dp.getCategoricalValues(), cats.get(k));
                k++;
            }
        }
        
        //Last set, contains any leftovers
        for( ; k < getSampleSize(); k++)
        {
            DataPoint dp = randSmpls.get(k);
            cvList.get(folds-1).addDataPoint(dp.getNumericalValues(), dp.getCategoricalValues(), cats.get(k));
        }
        
        return cvList;
    }
    
    public void addDataPoint(Vec v, int[] classes, int classification)
    {
        if(v.length() != numNumerVals)
            throw new RuntimeException("Data point does not contain enough numerical data points");
        if(classes.length != categories.length)
            throw new RuntimeException("Data point does not contain enough categorical data points");
        
        for(int i = 0; i < classes.length; i++)
            if(!categories[i].isValidCategory(classes[i]))
                throw new RuntimeException("Categoriy value given is invalid");
        
        classifiedExamples.get(classification).add(new DataPoint(v, classes, categories));
        
        numOfSamples++;
        
    }
    
    /**
     * Returns the list of all examples that belong to the given category. 
     * @param category the category desired
     * @return all given examples that belong to the given category
     */
    public List<DataPoint> getSamples(int category)
    {
        return new ArrayList<DataPoint>(classifiedExamples.get(category));
    }
    
    /**
     * 
     * @param category the category desired
     * @param n the n'th numerical variable
     * @return a vector of all the values for the n'th numerical variable for the given category
     */
    public Vec getSampleVariableVector(int category, int n)
    {
        List<DataPoint> categoryList = getSamples(category);
        DenseVector vec = new DenseVector(categoryList.size());
        
        for(int i = 0; i < vec.length(); i++)
            vec.set(i, categoryList.get(i).getNumericalValues().get(n));
        
        return vec;
    }
    
    /**
     * Returns summary statistics computed in an online fashion for each numeric
     * variable. This consumes less memory, but can be less numerically stable. 
     * 
     * @return an array of summary statistics
     */
    public OnLineStatistics[] singleVarStats()
    {
        OnLineStatistics[] stats = new OnLineStatistics[numNumerVals];
        for(int i = 0; i < stats.length; i++)
            stats[i] = new OnLineStatistics();
        
        for(List<DataPoint> list : classifiedExamples)
            for(DataPoint dp: list)
            {
                Vec v = dp.getNumericalValues();
                for(int i = 0; i < numNumerVals; i++)
                    stats[i].add(v.get(i));
            }
        
        return stats;
    }
    
    /**
     * 
     * @return the array of {@link CategoricalData} for each of the categorical variables 
     * (excluding the classification category). 
     */
    public CategoricalData[] getCategories()
    {
        return categories;
    }
    
    
    public int getSampleSize()
    {
        return numOfSamples;
    }
    
    /**
     * 
     * @return the {@link CategoricalData} object for the variable that is to be predicted
     */
    public CategoricalData getPredicting()
    {
        return predicting;
    }
    
    public int getNumCategoricalVars()
    {
        return categories.length;
    }
    
    public int getNumNumericalVars()
    {
        return numNumerVals;
    }
    
    public List<DataPointPair<Integer>> getAsDPPList()
    {
        List<DataPointPair<Integer>> dataPoints = new ArrayList<DataPointPair<Integer>>(getSampleSize());
        for(int i = 0; i < predicting.getNumOfCategories(); i++)
            for(DataPoint dp : classifiedExamples.get(i))
                dataPoints.add(new DataPointPair<Integer>(dp, i));
        
        return dataPoints;
    }
}