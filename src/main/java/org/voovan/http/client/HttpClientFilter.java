package org.voovan.http.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.voovan.http.message.HttpParser;
import org.voovan.http.message.Request;
import org.voovan.network.IoFilter;
import org.voovan.network.IoSession;
import org.voovan.network.exception.IoFilterException;
import org.voovan.tools.TObject;

/**
 * HTTP 请求过滤器
 * @author helyho
 * 
 * Voovan Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class HttpClientFilter implements IoFilter {

	@Override
	public Object encode(IoSession session,Object object) {
		if(object instanceof Request){
			Request request = TObject.cast(object);
			return ByteBuffer.wrap(request.asBytes());
		}
		return null;
	}

	@Override
	public Object decode(IoSession session,Object object) throws IoFilterException{
		try{
			if(object instanceof ByteBuffer){
				ByteBuffer byteBuffer = TObject.cast(object);
				return HttpParser.parseResponse(new ByteArrayInputStream(byteBuffer.array()));
			}
		}catch(IOException e){
			throw new IoFilterException("HttpClientFilter decode Error. "+e.getMessage(),e);
		}
		return null;
	}
}