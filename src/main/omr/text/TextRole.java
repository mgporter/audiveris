//----------------------------------------------------------------------------//
//                                                                            //
//                              T e x t R o l e                               //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright © Hervé Bitteur 2000-2012. All rights reserved.                 //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.text;

import omr.constant.ConstantSet;

import omr.log.Logger;

import omr.score.common.PixelPoint;
import omr.score.common.PixelRectangle;
import omr.score.entity.ScoreSystem;
import omr.score.entity.Staff;
import omr.score.entity.SystemNode.StaffPosition;
import omr.score.entity.SystemPart;
import omr.score.entity.Text;

import omr.sheet.Scale;
import omr.sheet.Sheet;
import omr.sheet.SystemInfo;

/**
 * Class {@code TextRole} describes the role of a piece of text
 * (typically a sentence).
 *
 * @author Hervé Bitteur
 */
public enum TextRole
{
    //~ Enum constants ---------------------------------------------------------

    /** No role known. */
    UnknownRole,
    /** (Part of) lyrics. */
    Lyrics,
    /** Title of the opus. */
    Title,
    /** Playing instruction. */
    Direction,
    /** Number for this opus. */
    Number,
    /** Name for the part. */
    Name,
    /** A creator (composer, etc...). */
    Creator,
    /** Copyright notice. */
    Rights,
    /** Chord mark. */
    Chord;

    //~ Static fields/initializers ---------------------------------------------
    //
    /** Specific application parameters. */
    private static final Constants constants = new Constants();

    /** Usual logger utility. */
    private static final Logger logger = Logger.getLogger(TextRole.class);

    //~ Methods ----------------------------------------------------------------
    //
    //-----------------//
    // getStringHolder //
    //-----------------//
    /**
     * Forge a string to be used in lieu of real text value.
     *
     * @param NbOfChars the number of characters desired
     *
     * @return a dummy string of NbOfChars chars
     */
    public String getStringHolder (int NbOfChars)
    {
        if (NbOfChars > 1000) {
            logger.warning("Abnormal text length:{0}", NbOfChars);

            return "<<" + Integer.toString(NbOfChars) + ">>";
        } else {
            StringBuilder sb = new StringBuilder("[");

            while (sb.length() < (NbOfChars - 1)) {
                sb.append(toString())
                        .append("-");
            }

            return sb.substring(0, Math.max(NbOfChars - 1, 0)) + "]";
        }
    }

    //-----------//
    // guessRole //
    //-----------//
    /**
     * Try to infer the role of this textual item.
     * For the time being, this is a simple algorithm based on sentence location
     * within the page.
     *
     * @param box        the sentence bounds
     * @param systemInfo the containing system
     * @param isItalic   indicates if the item is (mainly) in italics
     *                   (null if unknown)
     * @return the role information inferred for the provided sentence glyph
     */
    public static TextRoleInfo guessRole (PixelRectangle box,
                                          SystemInfo systemInfo,
                                          Boolean isItalic)
    {
        if (box == null) {
            return null;
        }

        Sheet sheet = systemInfo.getSheet();
        ScoreSystem system = systemInfo.getScoreSystem();
        Scale scale = system.getScale();
        PixelPoint left = new PixelPoint(box.x, box.y + (box.height / 2));
        PixelPoint right = new PixelPoint(box.x + box.width,
                box.y + (box.height / 2));

        // First system in page?
        boolean firstSystem = system.getId() == 1;

        // Last system in page?
        boolean lastSystem = sheet.getSystems()
                .size() == system.getId();

        // Vertical position wrt (system) staves
        StaffPosition systemPosition = system.getStaffPosition(left);

        // Vertical position wrt (part) staves
        SystemPart part = system.getPartAbove(left);
        StaffPosition partPosition = part.getStaffPosition(left);

        // Vertical distance from staff?
        Staff staff = system.getStaffAt(left);
        int staffDy = Math.abs(staff.getTopLeft().y - box.y);
        boolean closeToStaff = staffDy
                               <= scale.toPixels(constants.maxStaffDy);

        // Begins before the part?
        boolean leftOfStaves = system.isLeftOfStaves(left);

        // At the center of page width?
        int maxCenterDx = scale.toPixels(constants.maxCenterDx);
        int pageCenter = sheet.getWidth() / 2;
        boolean pageCentered = Math.abs((box.x + (box.width / 2))
                                        - pageCenter) <= maxCenterDx;

        // Right aligned with staves?
        int maxRightDx = scale.toPixels(constants.maxRightDx);
        boolean rightAligned = Math.abs(right.x - system.getTopLeft().x
                                        - system.getDimension().width)
                               <= maxRightDx;

        // Short Sentence?
        int maxShortLength = scale.toPixels(constants.maxShortLength);
        boolean shortSentence = box.width <= maxShortLength;

        // Tiny Sentence?
        int maxTinyLength = scale.toPixels(constants.maxTinyLength);
        boolean tinySentence = box.width <= maxTinyLength;

        // High text?
        int minTitleHeight = scale.toPixels(constants.minTitleHeight);
        boolean highText = box.height >= minTitleHeight;

        logger.fine("{0} firstSystem={1} lastSystem={2} systemPosition={3}"
                    + " partPosition={4} closeToStaff={5} leftOfStaves={6}"
                    + " pageCentered={7} rightAligned={8} shortSentence={9}"
                    + " highText={10}",
                box, firstSystem, lastSystem, systemPosition,
                partPosition, closeToStaff, leftOfStaves, pageCentered,
                rightAligned, shortSentence, highText);

        // Decisions ...
        switch (systemPosition) {
        case ABOVE_STAVES: // Title, Number, Creator, Direction (Chord)

            if (tinySentence) {
                return new TextRoleInfo(TextRole.UnknownRole);
            }

            if (firstSystem) {
                if (leftOfStaves) {
                    return new TextRoleInfo(
                            TextRole.Creator,
                            Text.CreatorText.CreatorType.lyricist);
                } else if (rightAligned) {
                    return new TextRoleInfo(
                            TextRole.Creator,
                            Text.CreatorText.CreatorType.composer);
                } else if (closeToStaff) {
                    return new TextRoleInfo(TextRole.Direction);
                } else if (pageCentered) { // Title, Number
                    if (highText) {
                        return new TextRoleInfo(TextRole.Title);
                    } else {
                        return new TextRoleInfo(TextRole.Number);
                    }
                }
            } else {
                return new TextRoleInfo(TextRole.Direction);
            }

            break;

        case WITHIN_STAVES: // Name, Lyrics, Direction

            if (leftOfStaves) {
                return new TextRoleInfo(TextRole.Name);
            } else if (partPosition == StaffPosition.BELOW_STAVES
                       && (isItalic == null || !isItalic)) {
                return new TextRoleInfo(TextRole.Lyrics);
            } else {
                return new TextRoleInfo(TextRole.Direction);
            }

        case BELOW_STAVES: // Copyright

            if (tinySentence) {
                return new TextRoleInfo(TextRole.UnknownRole);
            }

            if (pageCentered && shortSentence && lastSystem) {
                return new TextRoleInfo(TextRole.Rights);
            }
        }

        // Default
        return new TextRoleInfo(TextRole.UnknownRole);
    }

    //~ Inner Classes ----------------------------------------------------------
    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
            extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        Scale.Fraction maxRightDx = new Scale.Fraction(
                2,
                "Maximum horizontal distance on the right end of the staff");

        Scale.Fraction maxCenterDx = new Scale.Fraction(
                30,
                "Maximum horizontal distance around center of page");

        Scale.Fraction maxShortLength = new Scale.Fraction(
                35,
                "Maximum length for a short sentence (no lyrics)");

        Scale.Fraction maxTinyLength = new Scale.Fraction(
                2,
                "Maximum length for a tiny sentence (no lyrics)");

        Scale.Fraction maxStaffDy = new Scale.Fraction(
                7,
                "Maximum distance above staff for a direction");

        Scale.Fraction minTitleHeight = new Scale.Fraction(
                3,
                "Minimum height for a title text");

    }
}