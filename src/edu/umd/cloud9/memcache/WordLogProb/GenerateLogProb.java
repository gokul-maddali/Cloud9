package edu.umd.cloud9.memcache.WordLogProb;

/**
 * @author Samantha Mahindrakar
 */

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;

public class GenerateLogProb {

	// this records the total number of words in the document
	private static long totalWords=0;

	public static class MyMapper extends MapReduceBase implements
	Mapper<LongWritable, Text, Text, LongWritable> {

		// reuse objects to save overhead of object creation
		private final static LongWritable one = new LongWritable(1);
		private Text word = new Text();

		/**
		 * @param key - line number in the sequence file
		 * @param value - actual line of text in the sequence file
		 * @param output - the value sent to the reducer
		 * @return
		 */
		public void map(LongWritable key, Text value, OutputCollector<Text, LongWritable> output,
				Reporter reporter) throws IOException {
			
			// read one line at a time for the Sequence file and
			// tokenize it into words
			String line = ((Text) value).toString();
			StringTokenizer itr = new StringTokenizer(line);
			
			while (itr.hasMoreTokens()) {
				word.set(itr.nextToken());
				
				// output the word and its count to the reducer
				output.collect(word, one);
			}
		}
	}

	public static class MyReducer extends MapReduceBase implements
	Reducer<Text, LongWritable, Text, FloatWritable> {

		// reuse objects
		private final static FloatWritable SumValue = new FloatWritable();

		/**
		 * @param key - word
		 * @param values - all values associated with the key
		 * @param output - the <word,logProbability> generated by the reducer
		 * @return
		 */
		public void reduce(Text key, Iterator<LongWritable> values,
				OutputCollector<Text, FloatWritable> output, Reporter reporter) throws IOException {
			
			// sum up all the values associated to a specific key
			long sum = 0;
			while (values.hasNext()) {
				sum += values.next().get();
			}

			double d= (double)sum/(double)totalWords;
			double tempD = Math.log(d);
			SumValue.set((float)tempD);
			output.collect(key, SumValue);
		}
	}

	public static void main(String[] args)throws IOException {
			
		/*
         * First argument - path of the input file on the local file system
         * Second argument - path of the output sequence file where each word
         * and its corresponding log probabilities are stored
         */

        if(args.length != 2){
            System.out.println(" usage : [path of input text file] [path of seq file]");
            System.exit(1);
        }
		String inputPath = args[0];
		String outputPath = args[1];

		// since this is run locally, the number of mappers is set to 1
		int mapTasks = 1;
		int reduceTasks = 1;

		totalWords=countWords(inputPath);
		JobConf conf = new JobConf(GenerateLogProb.class);
		conf.setJobName("GenerateLogProb");
		conf.setNumMapTasks(mapTasks);
		conf.setNumReduceTasks(reduceTasks);
		FileInputFormat.setInputPaths(conf, new Path(inputPath));
		FileOutputFormat.setOutputPath(conf, new Path(outputPath));
		FileOutputFormat.setCompressOutput(conf, false);
		conf.setOutputFormat(SequenceFileOutputFormat.class);

		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(FloatWritable.class);
		conf.setMapOutputKeyClass(Text.class);
		conf.setMapOutputValueClass(LongWritable.class);

		conf.setMapperClass(MyMapper.class);
		conf.setReducerClass(MyReducer.class);

		// Delete the output directory if it exists already
		Path outputDir = new Path(outputPath);
		FileSystem.get(conf).delete(outputDir, true);

		JobClient.runJob(conf);
	}

	/**
	 * @method finds the total number of words in the document
	 * @param inputPath - takes the path of the input File on the local system
	 * @return
	 * @throws IOException
	 */
	public static long countWords(String inputPath) throws IOException{
		
		BufferedReader reader = new BufferedReader(
										new InputStreamReader(
											new FileInputStream(inputPath)));
		String line;
		long count=0;
		while((line=reader.readLine())!=null){
			
			StringTokenizer token = new StringTokenizer(line);
			
			while(token.hasMoreTokens()){
				token.nextToken();
				count++;
			}
		}	
		
		// returns the total number of words in the document
		return count;
	}
}
