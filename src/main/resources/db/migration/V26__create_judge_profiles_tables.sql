CREATE TABLE judge_profiles (
    id                            UUID PRIMARY KEY,
    user_id                       UUID NOT NULL UNIQUE REFERENCES users(id),
    qualification_details         VARCHAR(200),
    preferred_comment_language    VARCHAR(5),
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                    TIMESTAMP WITH TIME ZONE
);

CREATE TABLE judge_profile_certifications (
    judge_profile_id  UUID NOT NULL REFERENCES judge_profiles(id) ON DELETE CASCADE,
    certification     VARCHAR(20) NOT NULL,
    PRIMARY KEY (judge_profile_id, certification)
);
