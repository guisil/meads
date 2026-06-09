package app.meads.judging.internal;

import app.meads.entry.Entry;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.springframework.util.StringUtils;

/**
 * Read-only "mead details" dialog for the judging round views — lets a judge or
 * admin see an entry's objective, tasting-relevant characteristics from any
 * round row. Modelled on the entry-admin "view entry" dialog, but deliberately
 * omits the <strong>mead name</strong>, <strong>status</strong> and
 * <strong>entrant</strong> (anonymity — judges judge to style, not to a brand or
 * person), and the category (every entry on a round shares it, so it adds no
 * information here). The entry is identified by its anonymized code only.
 *
 * <p>Field labels reuse the existing {@code entry-admin.entries.view.*} keys to
 * avoid duplicating ten generic labels across five locales.
 */
class MeadDetailsDialog extends Dialog {

    MeadDetailsDialog(Entry entry) {
        setHeaderTitle(getTranslation("round.mead-details.title", entry.getEntryCode()));
        setId("mead-details-dialog");
        setWidth("520px");

        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.add(field("entry-admin.entries.view.sweetness", entry.getSweetness().getDisplayName()));
        layout.add(field("entry-admin.entries.view.strength", entry.getStrength().getDisplayName()));
        layout.add(field("entry-admin.entries.view.abv", entry.getAbv() + "%"));
        layout.add(field("entry-admin.entries.view.carbonation", entry.getCarbonation().getDisplayName()));
        layout.add(field("entry-admin.entries.view.honey", entry.getHoneyVarieties()));
        if (StringUtils.hasText(entry.getOtherIngredients())) {
            layout.add(field("entry-admin.entries.view.other-ingredients", entry.getOtherIngredients()));
        }
        layout.add(field("entry-admin.entries.view.wood-aged",
                getTranslation(entry.isWoodAged()
                        ? "entry-admin.entries.view.wood-aged.yes"
                        : "entry-admin.entries.view.wood-aged.no")));
        if (entry.isWoodAged() && StringUtils.hasText(entry.getWoodAgeingDetails())) {
            layout.add(field("entry-admin.entries.view.wood-details", entry.getWoodAgeingDetails()));
        }
        if (StringUtils.hasText(entry.getAdditionalInformation())) {
            layout.add(field("entry-admin.entries.view.additional-info", entry.getAdditionalInformation()));
        }
        add(layout);
        getFooter().add(new Button(getTranslation("button.close"), e -> close()));
    }

    private TextField field(String labelKey, String value) {
        var f = new TextField(getTranslation(labelKey));
        f.setValue(value != null ? value : "");
        f.setReadOnly(true);
        f.setWidthFull();
        return f;
    }
}
