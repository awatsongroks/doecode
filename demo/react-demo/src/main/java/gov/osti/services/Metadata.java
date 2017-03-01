/*
 */
package gov.osti.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.osti.connectors.Connector;
import gov.osti.entity.DOECodeMetadata;
import gov.osti.listeners.DoeServletContextListener;
import java.io.IOException;
import java.io.StringReader;
import javax.persistence.EntityManager;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Web Service for Metadata.
 * 
 * endpoints:
 * 
 * GET
 * metadata/{codeId} - retrieve instance of JSON for codeId
 * metadata/autopopulate?repo={url} - attempt an auto-populate Connector call for
 * indicated URL
 * 
 * POST
 * metadata - send JSON for persisting to the storage layer
 * metadata/submit - send JSON for posting to both ELINK and persistence layer
 *
 * @author ensornl
 */
@Path("metadata")
public class Metadata {
    // logger instance
    private static Logger log = LoggerFactory.getLogger(Metadata.class);
    
    @Context
    private UriInfo context;
    
    /**
     * Creates a new instance of MetadataResource
     */
    public Metadata() {
        
    }
    
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * Attempt to look up and return a Metadata record.
     * 
     * Produces:  application/json
     * 
     * @param codeId the Metadata codeId to look for
     * @return JSON of the Metadata if found
     */
    @GET
    @Path ("{codeId}")
    @Produces (MediaType.APPLICATION_JSON)
    public Response load(@PathParam ("codeId") Long codeId ) {
        EntityManager em = DoeServletContextListener.createEntityManager();
        
        try {
            DOECodeMetadata md = em.find(DOECodeMetadata.class, codeId);
            
            if ( null==md )
                throw new NotFoundException("ID not on file.");
            
            // just send it back
            return Response
                    .status(Response.Status.OK)
                    .entity(mapper.createObjectNode().putPOJO("metadata", md.toJson()).toString())
                    .build();
        } finally {
            em.close();
        }
    }
    
    /**
     * Call to auto-populate Metadata information via Connector, if possible.
     * 
     * @param url the REPOSITORY URL to look up information from
     * @return a Metadata instance (JSON) if information was found
     */
    @GET
    @Path ("/autopopulate")
    @Produces (MediaType.APPLICATION_JSON)
    public Response autopopulate(@QueryParam("repo") String url) {
        return Response
                .status(Response.Status.OK)
                .entity(mapper.createObjectNode().putPOJO("metadata", Connector.readProject(url)).toString())
                .build();
    }
    
    /**
     * POST a Metadata JSON object to the persistence layer.
     * 
     * @param object the JSON to post
     * @return the JSON after persistence; perhaps containing assigned codeId, etc.
     */
    @POST
    @Consumes ( MediaType.APPLICATION_JSON )
    @Produces ( MediaType.APPLICATION_JSON )
    public Response save(String object) {
        EntityManager em = DoeServletContextListener.createEntityManager();
        
        try {
            DOECodeMetadata md = DOECodeMetadata.parseJson(new StringReader(object));

            if ( 0==md.getCodeId() )
                em.persist(md);
            else
                em.merge(md);
            
            return Response
                    .status(200)
                    .entity(mapper.createObjectNode().putPOJO("metadata", md.toJson()).toString())
                    .build();
        } catch ( IOException e ) {
            throw new InternalServerErrorException("IO Error: " + e.getMessage());
        } finally {
            em.close();
        }
    }
}