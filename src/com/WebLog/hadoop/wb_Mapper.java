package com.WebLog.hadoop;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.param.util.ParamUtil;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
public class wb_Mapper extends Mapper <LongWritable, Text, NullWritable, Text>
{
//	50.57.190.149 - - [22/Apr/2012:07:12:42 +0530] "GET /a/b/c/d?p=10 HTTP/1.0" 200 12530 "-" "-"
	private static String LOG_PATTERN = "^(\\S+) (\\S+) (\\S+) \\[(.+?)\\] \"([^\"]*)\" (\\S+) (\\S+) \"([^\"]*)\" \"([^\"]*)\"";
	private static int NUM_FIELDS = 9;
	private Pattern pattern  = null;
	MultipleOutputs <NullWritable, Text> mos = null;

	public void setup(Context context)
    {
		mos = new MultipleOutputs <NullWritable, Text> (context);
		pattern = Pattern.compile(LOG_PATTERN);
    }
	
	protected void cleanup(Context context) throws IOException, InterruptedException
	{
		mos.close();
	}
	
	public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException 
	{
		String formattedValue = value.toString().replaceAll("\t", " ").trim();				//remove \t as it is delimiter
		
		Matcher matcher = pattern.matcher(formattedValue);
		if (matcher.matches() && NUM_FIELDS == matcher.groupCount())
		{
			String requestString = matcher.group(5);													//Request String = Method request protocol
			String separateReqCategory = getSeparateReqCategories(requestString);
			
			StringBuffer valueBuffer = new StringBuffer();
			valueBuffer.append(matcher.group(1)).append(ParamUtil.DELIMITER_TAB);					//remoteIP  14.97.118.184
			valueBuffer.append(matcher.group(2)).append(ParamUtil.DELIMITER_TAB);					//remotelogname
			valueBuffer.append(matcher.group(3)).append(ParamUtil.DELIMITER_TAB);					//user
			valueBuffer.append(matcher.group(4)).append(ParamUtil.DELIMITER_TAB);					//time
			valueBuffer.append(matcher.group(5)).append(ParamUtil.DELIMITER_TAB);					//requeststr
			
			valueBuffer.append(separateReqCategory).append(ParamUtil.DELIMITER_TAB);				//cat1 cat2 cat3 cat4 page param
			
			valueBuffer.append(matcher.group(6)).append(ParamUtil.DELIMITER_TAB);					//statuscode
			valueBuffer.append(matcher.group(7)).append(ParamUtil.DELIMITER_TAB);					//bytestring
			valueBuffer.append(matcher.group(8)).append(ParamUtil.DELIMITER_TAB);					//user-agent
			valueBuffer.append(matcher.group(9));													//referral
			
//			context.write(NullWritable.get(), new Text (valueBuffer.toString()));
			mos.write("ParsedRecords", NullWritable.get(), new Text (valueBuffer.toString()));
		}
		else
		{
			mos.write("BadRecords", NullWritable.get(), value);
		}
	}
	
	private String getSeparateReqCategories(String requestString)
	{
		String requestStringTokens [] = requestString.split(" ");
		
		String separateReqCategory = null;
		if (requestStringTokens.length == 3)
			separateReqCategory = getProcessedRequest(requestStringTokens[1]);
		else
			separateReqCategory = getProcessedDefaultRequest();
		return separateReqCategory;
	}
	
	String getProcessedRequest(String request)						//	/a/b/c/d?param
	{
		StringBuffer separateReqCategoryBuffer = new StringBuffer();
		
		String requestParamTokens [] = request.split("\\?");						//reqParam = /a/b?aaa
		String ParamString = "-";													//paramString = aaa=1&bbb=2
		boolean paramFlag = false;
		if (requestParamTokens.length == 2)											//? one time
		{
			paramFlag = true;
			ParamString = requestParamTokens[1];
		}
		else if (requestParamTokens.length > 2)										//? More than one time
		{
			paramFlag = true;
			StringBuffer paramStrBuff = new StringBuffer();
			for (int cnt = 1; cnt < requestParamTokens.length; cnt++)
			{
				paramStrBuff.append(requestParamTokens[cnt]);
				if (cnt < requestParamTokens.length - 1)
					paramStrBuff.append(ParamUtil.DELIMITER_QUESTIONMARK);
			}
			ParamString = paramStrBuff.toString();
		}
		
		String requestTokens [] = null;
		if (paramFlag)
			requestTokens = requestParamTokens[0].split("/");			//Request = /a/b/c	(case for /a/b/c?param)
		else
			requestTokens = request.split("/");							//Request = /a/b/c	(case for /a/b/c)
		
		
		int requestTokensLen = requestTokens.length;
		if (requestTokensLen == 0)													// for /
		{
			separateReqCategoryBuffer.append("/").append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH);
		}
		else if (requestTokensLen == 1)										//situation never come		(for a/)
		{
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH);
		}
		else if (requestTokensLen == 2)										// for /abc.html
		{
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[1]);
		}
		else if (requestTokensLen == 3)										//for /a/abc.html
		{
			separateReqCategoryBuffer.append(requestTokens[1]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[2]);
		}
		else if (requestTokensLen == 4)										// for /a/b/abc.html
		{
			separateReqCategoryBuffer.append(requestTokens[1]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[2]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[3]);
		}
		else if (requestTokensLen == 5)
		{
			separateReqCategoryBuffer.append(requestTokens[1]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[2]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[3]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[4]);
		}
		else if (requestTokensLen == 6)
		{
			separateReqCategoryBuffer.append(requestTokens[1]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[2]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[3]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[4]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[5]);
		}
		else if (requestTokensLen > 6)
		{
			separateReqCategoryBuffer.append(requestTokens[1]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[2]).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[3]).append(ParamUtil.DELIMITER_TAB);
			StringBuffer requestTokensBuffer = new StringBuffer();
			for (int cnt = 4; cnt < requestTokensLen - 1; cnt++)
			{
				requestTokensBuffer.append(requestTokens[cnt]).append("/");
			}
			separateReqCategoryBuffer.append(requestTokensBuffer).append(ParamUtil.DELIMITER_TAB);
			separateReqCategoryBuffer.append(requestTokens[requestTokensLen - 1]);
		}
		
		
//		separateReqCategoryBuffer.append(requestStringTokens[2]).append(DELIMITER_TAB);
		separateReqCategoryBuffer.append(ParamUtil.DELIMITER_TAB).append(ParamString);
		return separateReqCategoryBuffer.toString();
	}
	
	String getProcessedDefaultRequest()
	{
		StringBuffer separateReqCategoryBuffer = new StringBuffer();
		
		separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);				//cat1
		separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);				//cat2
		separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);				//cat3
		separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);				//cat4
		separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH).append(ParamUtil.DELIMITER_TAB);				//page
		
		separateReqCategoryBuffer.append(ParamUtil.DEFAULT_VALUE_DASH);										//param

		return separateReqCategoryBuffer.toString();
	}
}
