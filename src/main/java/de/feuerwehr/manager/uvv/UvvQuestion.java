package de.feuerwehr.manager.uvv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "uvv_questions")
public class UvvQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private UvvCampaign campaign;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "question_text", nullable = false, length = 1024)
    private String questionText;

    @Column(name = "option_a", nullable = false, length = 512)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 512)
    private String optionB;

    @Column(name = "option_c", length = 512)
    private String optionC;

    @Column(name = "option_d", length = 512)
    private String optionD;

    @Column(name = "correct_option", nullable = false, length = 1)
    private String correctOption;
}
