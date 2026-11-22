package com.tounsiguide.locationservice.association;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint called by the Community Service after a post or comment is created, edited or deleted.
 * Not routed by the Gateway. Answers 202 at once: the post is read and analysed in the background.
 */
@RestController
public class InternalPostController {

    private final AssociationAnalysisJob job;

    public InternalPostController(AssociationAnalysisJob job) {
        this.job = job;
    }

    @PostMapping("/internal/posts/{postId}/changed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void postChanged(@PathVariable long postId) {
        job.postChangedLater(postId);
    }
}
